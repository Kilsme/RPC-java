package tech.insight.kilsme.rpc.consumser;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioSocketChannel;
import tech.insight.kilsme.rpc.api.Add;
import tech.insight.kilsme.rpc.codec.KilsmeDecoder;
import tech.insight.kilsme.rpc.codec.RequestEncoder;
import tech.insight.kilsme.rpc.exception.RpcException;
import tech.insight.kilsme.rpc.message.Request;
import tech.insight.kilsme.rpc.message.Response;
import tech.insight.kilsme.rpc.codec.ResponseEncoder;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

// RPC 消费端：负责发起请求并等待 Provider 返回结果。
public class Consumer implements Add {
    //在途请求，没有拿到response的request
    private Map<Integer, CompletableFuture<?>> inFlightRequestTable = new ConcurrentHashMap<>();
     //拿到连接管理器
    private ConnectionManager manager=new ConnectionManager(crateBootstrap());
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
                                        CompletableFuture requestFuture = inFlightRequestTable.remove(response.getRequestId());
                                        if (response.getCode() == 200) {
                                            requestFuture.complete(Integer.valueOf(response.getRes().toString()));
                                        } else {
                                            requestFuture.completeExceptionally(new RpcException(response.getErrorMessage()));

                                        }
                                    }
                                });
                    }
                });
        return bootstrap;
    }
    @Override
    public int add(int a, int b) {
        try {
            // 用 Future 承接异步响应，最后在方法尾部 get() 同步返回。
            CompletableFuture<Integer> completableFuture = new CompletableFuture<>();
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
                if (f.isSuccess()) {
                    inFlightRequestTable.put(request.getRequestId(), completableFuture);
                }
            });
            // 同步等待异步结果返回。 这个是阻塞等待
            return completableFuture.get(3, TimeUnit.SECONDS);
        } catch (Exception e) {
            throw new RuntimeException("RPC 调用异常");
        }

    }
}



