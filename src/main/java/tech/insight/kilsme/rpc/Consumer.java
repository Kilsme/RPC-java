package tech.insight.kilsme.rpc;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.LineBasedFrameDecoder;
import io.netty.handler.codec.string.StringDecoder;
import io.netty.handler.codec.string.StringEncoder;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

public class Consumer {
    public int add(int a, int b) throws InterruptedException, ExecutionException {
        // 用 CompletableFuture 接收服务端返回的结果：
        // 先发起网络请求，等响应回来后再把结果 complete 进去。
        CompletableFuture<Integer> completableFuture = new CompletableFuture<>();

        // Netty 客户端启动器：负责建立到服务端的连接。
        Bootstrap bootstrap = new Bootstrap();
        bootstrap.group(new NioEventLoopGroup(4))
                .channel(NioSocketChannel.class)
                // 为“客户端连接”初始化 pipeline：编解码器 + 入站处理器。
                .handler(new ChannelInitializer<NioSocketChannel>() {
                    @Override
                    protected void initChannel(NioSocketChannel nioSocketChannel) throws Exception {
                        // 按行拆包：消息必须以换行结尾，服务端/客户端才能正确分帧。
                        nioSocketChannel.pipeline().addLast(new LineBasedFrameDecoder(1024))
                                // ByteBuf -> String，把收到的字节消息转成字符串。
                                .addLast(new StringDecoder())
                                // String -> ByteBuf，发送字符串时自动编码。
                                .addLast(new StringEncoder())
                                // 业务处理器：收到服务端响应后，在这里处理结果。
                                .addLast(new SimpleChannelInboundHandler<String>() {
                                    @Override
                                    protected void channelRead0(ChannelHandlerContext channelHandlerContext, String msg) throws Exception {
                                        // 服务端返回的是一个整数结果字符串，这里转成 int。
                                        int res = Integer.parseInt(msg);
                                        // 把结果交给主线程等待的 CompletableFuture。
                                        completableFuture.complete(res);
                                        // 结果已收到，关闭当前连接。
                                        channelHandlerContext.close();
                                    }
                                });
                    }
                });
        // 连接服务端并等待连接建立完成。
        ChannelFuture channelFuture = bootstrap.connect("localhost", 8888).sync();
        // 按约定的 RPC 协议发送请求：method,a,b\n
        channelFuture.channel().writeAndFlush("add," + a + "," + b + "\n");
        // 阻塞等待服务端响应结果。
        return completableFuture.get();
    }
}



