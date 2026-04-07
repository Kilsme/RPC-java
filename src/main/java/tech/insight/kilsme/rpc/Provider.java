package tech.insight.kilsme.rpc;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.LineBasedFrameDecoder;
import io.netty.handler.codec.string.StringDecoder;
import io.netty.handler.codec.string.StringEncoder;

import static java.awt.AWTEventMulticaster.add;

public class Provider {
    public static void main(String[] args) throws InterruptedException {
        ServerBootstrap serverBootstrap = new ServerBootstrap();
        serverBootstrap.group(new NioEventLoopGroup(), new NioEventLoopGroup(4))
                .channel(NioServerSocketChannel.class)
                .childHandler(new ChannelInitializer<NioSocketChannel>() {
                    @Override
                    protected void initChannel(NioSocketChannel nioSocketChannel) throws Exception {
                        nioSocketChannel.pipeline().addLast(new LineBasedFrameDecoder(1024))
                                .addLast(new StringDecoder())
                                .addLast(new StringEncoder())
                                .addLast(new SimpleChannelInboundHandler<String>() {
                                    //method,1,2
                                    @Override
                                    protected void channelRead0(ChannelHandlerContext channelHandlerContext, String msg) throws Exception {
                                        String[] s = msg.split(",");
                                        String method = s[0];
                                        int a = Integer.parseInt(s[1]);
                                        int b = Integer.parseInt(s[2]);
                                        if (method.equals("add")) {
                                            int res = add(a, b);
                                            //编码
                                            channelHandlerContext.writeAndFlush(res + "\n");
                                        }
                                    }
                                });
                    }
                });
        serverBootstrap.bind(8888).sync();
    }

    private static int add(int a, int b) {

        return a + b;
    }
}
