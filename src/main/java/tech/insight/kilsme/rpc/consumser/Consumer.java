package tech.insight.kilsme.rpc.consumser;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioSocketChannel;
import lombok.extern.slf4j.Slf4j;
import tech.insight.kilsme.rpc.api.Add;
import tech.insight.kilsme.rpc.codec.KilsmeDecoder;
import tech.insight.kilsme.rpc.codec.RequestEncoder;
import tech.insight.kilsme.rpc.exception.RpcException;
import tech.insight.kilsme.rpc.message.Request;
import tech.insight.kilsme.rpc.message.Response;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

// RPC 消费端：负责发起请求并等待 Provider 返回结果。
@Slf4j
public class Consumer implements Add {
    // 在途请求表：requestId -> 等待结果的 Future。
    private Map<Integer, CompletableFuture<Response>> inFlightRequestTable = new ConcurrentHashMap<>();
     // 连接复用管理器，统一维护到 Provider 的 TCP 连接。
    private ConnectionManager manager=new ConnectionManager(crateBootstrap());
    // 创建 Consumer 侧 Netty 客户端配置。
    private  Bootstrap crateBootstrap(){
        // Bootstrap 对应“客户端连接配置”。
        Bootstrap bootstrap = new Bootstrap();
        bootstrap.group(new NioEventLoopGroup(4))
                .channel(NioSocketChannel.class)
                // 客户端只有一个连接，因此使用 handler 初始化该连接的 pipeline。
                .handler(new ChannelInitializer<NioSocketChannel>() {
                    @Override
                    protected void initChannel(NioSocketChannel nioSocketChannel) throws Exception {
                        // 入站：先按协议解码，再交给业务 handler 处理 Response。
                        // 出站：RequestEncoder 在 writeAndFlush(Request) 时自动生效。
                        nioSocketChannel.pipeline().addLast(new KilsmeDecoder())
                                .addLast(new RequestEncoder())
                                // 业务入站处理器：收到响应后完成 Future，并关闭连接。
                                .addLast(new SimpleChannelInboundHandler<Response>() {
                                    @Override
                                    protected void channelRead0(ChannelHandlerContext channelHandlerContext, Response response) throws Exception {
                                        //返回了相应的response请求
                                        CompletableFuture<Response> responseFuture = inFlightRequestTable.remove(response.getRequestId());
                                        if(responseFuture ==null){
                                            log.warn("未找到对应的请求，requestId={}", response.getRequestId());
                                            return;
                                        }
                                        // 回填结果，唤醒 add() 中阻塞等待的线程。
                                        responseFuture.complete(response);

                                    }
                                });
                    }
                });
        return bootstrap;
    }
    @Override
    public Integer add(int a, int b) {
        try {
            // 用 Future 承接异步响应，最后在方法尾部 get() 同步返回。
            CompletableFuture<Response> responseCompletableFuture= new CompletableFuture<>();
            Channel channel = manager.getChannel("localhost", 8888);
            if(channel==null){
                throw  new RpcException("连接失败");
            }
            // 组装本次 RPC 请求。
            Request request = new Request();
            request.setMethodName("add");
            // request.setMethodName("privateAdd"); // 仅用于测试不存在方法的异常路径。
            request.setParams(new Object[]{a, b});
            request.setParamsClass(new Class[]{int.class, int.class});
            request.setServiceName(Add.class.getName());
            channel.writeAndFlush(request).addListener(f -> {
                if (f.isSuccess()){
                    // 仅在发送成功后登记 in-flight，避免永远收不到响应的脏记录。
                    inFlightRequestTable.put(request.getRequestId(), responseCompletableFuture);
                }
            });
            // 同步等待异步结果返回。 这个是阻塞等待
            Response response = responseCompletableFuture.get(3, TimeUnit.SECONDS);
            if (response.getCode() == 200) {
            return (Integer) response.getRes();
            } else {
                throw new RpcException(response.getErrorMessage());
            }

        } catch (RpcException rpcException) {
            throw  rpcException;
        }
        catch (Exception e) {
            throw new RuntimeException("RPC 调用异常");
        }

    }

    @Override
    public Integer minus(int a, int b) {
     // 当前 Consumer 示例只演示 add，minus 暂未实现远程调用。
     return 0;
    }
}



