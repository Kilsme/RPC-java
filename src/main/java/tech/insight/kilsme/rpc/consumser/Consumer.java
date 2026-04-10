package tech.insight.kilsme.rpc.consumser;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioSocketChannel;
import tech.insight.kilsme.rpc.api.Add;
import tech.insight.kilsme.rpc.codec.KilsmeDecoder;
import tech.insight.kilsme.rpc.codec.RequestEncoder;
import tech.insight.kilsme.rpc.message.Request;
import tech.insight.kilsme.rpc.message.Response;
import tech.insight.kilsme.rpc.codec.ResponseEncoder;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

// RPC 消费端：负责发起请求并等待 Provider 返回结果。
public class Consumer implements Add {
    @Override
    public int add(int a, int b){
        try{
            // 用 Future 承接异步响应，最后在方法尾部 get() 同步返回。
            CompletableFuture<Integer> completableFuture = new CompletableFuture<>();
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
                                            System.out.println(response);
                                            // 响应中的 res 是通用 Object，这里按 int 结果解析。
                                            Integer res = Integer.valueOf(response.getRes().toString());
                                            completableFuture.complete(Integer.valueOf(response.getRes().toString()));
                                            channelHandlerContext.close();
                                        }
                                    });
                        }
                    });

            // 建立到 Provider 的 TCP 连接。
            ChannelFuture channelFuture = bootstrap.connect("localhost", 8888).sync();
            // 组装本次 RPC 请求。
            Request request = new Request();
            request.setMethodName("add");
            // request.setMethodName("privateAdd"); // 仅用于测试不存在方法的异常路径。
            request.setParams(new Object[]{a,b});
            request.setParamsClass(new Class[]{int.class,int.class});
            request.setServiceName(Add.class.getName());
            channelFuture.channel().writeAndFlush(request);

            // 同步等待异步结果返回。
            return completableFuture.get();

        }catch (Exception e){
            throw new RuntimeException("RPC 调用异常");
        }

    }
}



