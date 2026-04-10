package tech.insight.kilsme.rpc.provider;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import tech.insight.kilsme.rpc.codec.KilsmeDecoder;
import tech.insight.kilsme.rpc.message.Request;
import tech.insight.kilsme.rpc.codec.ResponseEncoder;
import tech.insight.kilsme.rpc.message.Response;

import static java.awt.AWTEventMulticaster.add;

/**
 * RPC Provider 服务器：接收 Request，调用本地服务并返回 Response。
 */
public class ProviderServer {
    // boss 负责接收连接，worker 负责处理已建立连接上的 IO 事件。
    private EventLoopGroup bossEventLoopGroup;
    private EventLoopGroup workerEventLoopGroup;
    private final ProviderRegistry registry;
    private final int port;

    public ProviderServer(int port) {
        this.port = port;
        this.registry=new ProviderRegistry();
    }

    // 对外暴露注册入口，实际委托给注册中心。
    public <I> void register(Class<I> interfaceClass, I serviceInstance) {
        registry.register(interfaceClass, serviceInstance);
    }

    public void start() {
        // 服务端常见线程模型：1 组 accept 线程 + 1 组读写/业务线程。
        bossEventLoopGroup = new NioEventLoopGroup();
        workerEventLoopGroup = new NioEventLoopGroup(4);
        try {
            ServerBootstrap serverBootstrap = new ServerBootstrap();
            serverBootstrap.group(bossEventLoopGroup, workerEventLoopGroup)
                    .channel(NioServerSocketChannel.class)
                    // childHandler 作用于“每个新接入的客户端连接”的 pipeline。
                    // 与客户端 Bootstrap.handler 概念对应：客户端只有一个连接，
                    // 服务端会有很多子连接，所以使用 childHandler 初始化子连接。
                    .childHandler(new ChannelInitializer<NioSocketChannel>() {
                        @Override
                        protected void initChannel(NioSocketChannel nioSocketChannel) throws Exception {
                            // 入站：字节流 -> Request；出站：Response -> 字节流。
                            nioSocketChannel.pipeline()
                                    .addLast(new KilsmeDecoder())
                                    .addLast(new ResponseEncoder())
                                    .addLast(new SimpleChannelInboundHandler<Request>() {
                                        // 收到一个 RPC 请求后，执行业务并返回响应。
                                        @Override
                                        protected void channelRead0(ChannelHandlerContext channelHandlerContext, Request request) throws Exception {
                                            // 1) 根据 serviceName 找到服务实例。
                                            ProviderRegistry.invocation<?> service = registry.findService(request.getServiceName());
                                            // 2) 根据 methodName + paramTypes 反射调用。
                                            Object result = service.invoke(request.getMethodName(), request.getParamsClass(), request.getParams());
                                            System.out.println(request);
                                            String methodName = request.getMethodName();
                                            // 3) 将返回值封装为 Response 并写回客户端。
                                            Response response = new Response();
                                            response.setRes(result);
                                            channelHandlerContext.writeAndFlush(response);
                                        }
                                    });
                        }
                    });
            // 绑定端口并同步等待绑定成功。
            serverBootstrap.bind(port).sync();
        } catch (Exception e) {
            throw new RuntimeException("服务器启动异常");
        }
    }

    public void stop() {
        // 优雅关闭线程池，释放网络资源。
        if (bossEventLoopGroup != null) {
            bossEventLoopGroup.shutdownGracefully();
        }
        if (workerEventLoopGroup != null) {
            workerEventLoopGroup.shutdownGracefully();
        }
    }
}
