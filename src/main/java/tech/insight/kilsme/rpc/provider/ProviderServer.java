package tech.insight.kilsme.rpc.provider;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.timeout.IdleStateHandler;
import io.netty.util.AttributeKey;
import lombok.extern.slf4j.Slf4j;
import tech.insight.kilsme.rpc.handler.HeartbeatHandler;
import tech.insight.kilsme.rpc.codec.KilsmeDecoder;
import tech.insight.kilsme.rpc.codec.KilsmeEncoder;
import tech.insight.kilsme.rpc.compress.Compression;
import tech.insight.kilsme.rpc.compress.CompressionManager;
import tech.insight.kilsme.rpc.handler.TrafficRecordHandler;
import tech.insight.kilsme.rpc.limit.ConcurrencyLimiter;
import tech.insight.kilsme.rpc.limit.Limiter;
import tech.insight.kilsme.rpc.limit.RateLimiter;
import tech.insight.kilsme.rpc.message.Request;
import tech.insight.kilsme.rpc.message.Response;
import tech.insight.kilsme.rpc.register.DefaultServiceRegister;
import tech.insight.kilsme.rpc.register.ServiceMetadata;
import tech.insight.kilsme.rpc.register.ServiceRegistry;
import tech.insight.kilsme.rpc.serialize.Serializer;
import tech.insight.kilsme.rpc.serialize.SerializerManager;

import java.util.Locale;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Provider 端 Netty 服务器。
 * <p>
 * 核心职责：
 * 1) 启动监听端口并初始化 Netty pipeline（解码、编码、业务处理）；
 * 2) 启动后将本地已注册服务发布到注册中心；
 * 3) 收到 Request 后进行限流校验、服务查找、反射调用并回写 Response。
 * </p>
 */
@Slf4j
public class ProviderServer {
    // boss 负责接收连接，worker 负责处理已建立连接上的 IO 事件。
    // 这是 Netty 服务端的标准线程模型：主线程只负责 accept，工作线程负责真正读写和业务处理。
    private EventLoopGroup bossEventLoopGroup;
    private EventLoopGroup workerEventLoopGroup;
    private final ProviderRegistry registry;
    private final ServiceRegistry serviceRegister;
    private final ProviderProperties providerProperties;
    private final Limiter globallLimiter;
    private final SerializerManager serializerManager;
    private final CompressionManager compressionManager;
    private ThreadPoolExecutor invokeExecutor;

    public ProviderServer(ProviderProperties providerProperties) {
        this.providerProperties = providerProperties;
        this.serviceRegister = new DefaultServiceRegister();
        this.registry = new ProviderRegistry();
        this.globallLimiter = new ConcurrencyLimiter(providerProperties.getGlobalMaxRequest());
        this.serializerManager = new SerializerManager();
        this.compressionManager = new CompressionManager();
        this.invokeExecutor = new ThreadPoolExecutor(
                4,
                4,
                10,
                TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(1024),
                new FastFailResponseHandler());
    }

    /**
     * 对外暴露服务注册入口（接口 -> 实例）。
     *
     * <p>这里注册的是“本地实现对象”，并不是直接发到注册中心。
     * 先放到 ProviderRegistry 中，等服务端真正启动后，再统一把这些服务元数据注册出去。</p>
     */
    public <I> void register(Class<I> interfaceClass, I serviceInstance) {
        registry.register(interfaceClass, serviceInstance);
    }

    /**
     * 启动 Provider。
     *
     * <p>顺序：
     * <ol>
     *     <li>初始化注册中心客户端；</li>
     *     <li>启动 Netty 服务端监听；</li>
     *     <li>将本地注册的服务元数据发布到注册中心。</li>
     * </ol>
     * 先启动网络监听，再发布服务，能够减少“服务已注册但端口未就绪”的短暂异常窗口。</p>
     */
    public void start() {
        // 服务端常见线程模型：1 组 accept 线程 + 1 组读写/业务线程。
        bossEventLoopGroup = new NioEventLoopGroup();
        workerEventLoopGroup = new NioEventLoopGroup(providerProperties.getWorkThreadNum());
        try {
            this.serviceRegister.init(providerProperties.getRegistryConfig());
            ServerBootstrap serverBootstrap = new ServerBootstrap();
            serverBootstrap.group(bossEventLoopGroup, workerEventLoopGroup)
                    .channel(NioServerSocketChannel.class)
                    // childHandler 作用于“每个新接入的客户端连接”的 pipeline。
                    // 与客户端 Bootstrap.handler 概念对应：客户端一般自己维护连接；
                    // 服务端则需要对每一个新建立的子连接单独初始化处理链。
                    .childHandler(new ChannelInitializer<NioSocketChannel>() {
                        @Override
                        protected void initChannel(NioSocketChannel nioSocketChannel) throws Exception {
                            // 入站：字节流 -> Request；出站：Response -> 字节流。
                            // pipeline 顺序：先解码，再限流，再执行业务，最后编码响应。
                            nioSocketChannel.pipeline()
                                    .addLast(new TrafficRecordHandler())
                                    .addLast(new KilsmeDecoder())
                                    .addLast(new KilsmeEncoder())
                                    .addLast(new IdleStateHandler(30, 5, 0, TimeUnit.SECONDS))//增加心跳监控的hanlder
                                    .addLast(new HeartbeatHandler())
                                    .addLast(new LimitHandler())
                                    .addLast(new ProviderHandler());
                        }
                        //head-->decoder -->responseEncoder-->limitHandler(双向处理器)-->providerHandler-->tail
                    });
            // 绑定端口并同步等待绑定成功。
            serverBootstrap.bind(providerProperties.getHost(), providerProperties.getPort()).sync();
            // 将所有已注册的服务统一发布到注册中心中。
            registry.allServiceName().stream().map(this::buildMetadata).forEach(this.serviceRegister::registerService);
        } catch (Exception e) {
            throw new RuntimeException("服务器启动异常");
        }
    }

    /**
     * 组装单个服务的注册信息（serviceName -> host:port）。
     *
     * <p>这里的 metadata 是给注册中心看的“服务发现信息”，Consumer 会通过它找到实际可连接地址。</p>
     */
    private ServiceMetadata buildMetadata(String serviceName) {
        ServiceMetadata metadata = new ServiceMetadata();
        metadata.setServiceName(serviceName);
        metadata.setHost(providerProperties.getHost());
        metadata.setPort(providerProperties.getPort());
        return metadata;
    }

    public class LimitHandler extends ChannelDuplexHandler {//双向处理器，进行精确限流
        private static final AttributeKey<Limiter> CHANNEL_LIMITER_KEY = AttributeKey.valueOf("channel_limiter_key");
        private static final AttributeKey<AtomicInteger> GLOBAL_PERMITS = AttributeKey.valueOf("global_permits");

        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {//这个是向下传递的方法
            Request request = (Request) msg;
            // 第一道：整个 Provider 实例的全局并发限制。
            if (!globallLimiter.tryAcquire()) {
                ctx.writeAndFlush(Response.fail("provider限流", request.getRequestId()));
                return;
            }
            // 第二道：当前连接维度的速率限制。
            Limiter channelLimiter = ctx.channel().attr(CHANNEL_LIMITER_KEY).get();
            if (!channelLimiter.tryAcquire()) {
                globallLimiter.release();
                ctx.writeAndFlush(Response.fail("provider限流", request.getRequestId()));
                return;
            }
            ctx.channel().attr(GLOBAL_PERMITS).get().incrementAndGet();
            ctx.fireChannelRead(request);
        }

        @Override
        public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
            promise.addListener(future -> {
                // 响应写回后再释放许可，确保“占用 -> 处理 -> 归还”生命周期完整。
                int remain = ctx.channel().attr(GLOBAL_PERMITS).get().getAndDecrement();//进行减一
                if (remain > 0) {
                    ctx.channel().attr(CHANNEL_LIMITER_KEY).get().release();
                    globallLimiter.release();
                }
            });
            ctx.write(msg, promise);
        }

        @Override
        public void channelActive(ChannelHandlerContext ctx) throws Exception {
            // 当 channel 建立连接时，给 channel 绑定一个 limiter。
            // 这样每个客户端连接都有自己的速率窗口，避免单个 Consumer 把 Provider 打爆。
            Limiter channelLimiter = new RateLimiter(providerProperties.getPreConsumerMaxRequest());
            ctx.channel().attr(CHANNEL_LIMITER_KEY).set(channelLimiter);
            ctx.channel().attr(GLOBAL_PERMITS).set(new AtomicInteger(0));
            ctx.fireChannelActive();//将这个事件传播出去
        }

        @Override
        public void channelInactive(ChannelHandlerContext ctx) throws Exception {
            // 连接关闭时，把尚未归还的全局许可一次性释放掉，避免计数残留。
            int remain = ctx.channel().attr(GLOBAL_PERMITS).get().getAndSet(0);
            globallLimiter.release(remain);
            //传播这个事件
            ctx.fireChannelInactive();
        }
    }


    public class ProviderHandler extends SimpleChannelInboundHandler<Request> {
        /**
         * 收到一个 RPC 请求后，执行业务并返回响应。
         *
         * <p>处理链：服务定位 -> 反射调用 -> 构建 Response -> 回写客户端。
         * 这就是 Provider 端最核心的“请求执行器”。</p>
         */
        @Override
        protected void channelRead0(ChannelHandlerContext channelHandlerContext, Request request) throws Exception {
            // 1) 根据 serviceName 找到服务实例。
            // 这里查的是本地 ProviderRegistry，不是 Zookeeper。
            ProviderRegistry.invocation<?> invocation = registry.findService(request.getServiceName());
            // 2) 根据 methodName + paramTypes 反射调用。
            if (invocation == null) {
                Response failResp = Response.fail(String.format("%s 没有对应的服务", request.getServiceName()), request.getRequestId());
                channelHandlerContext.writeAndFlush(failResp);
                return;
            }
            EventLoop eventLoop = channelHandlerContext.channel().eventLoop();
            //将invoke行为提交给线程池去处理
            invokeExecutor.execute(new InvokeTask(request, channelHandlerContext, invocation));
            // 这里输出原始请求对象，方便调试时查看请求是否完整到达 Provider。
            System.out.println(request);
        }

        @Override
        public void channelActive(ChannelHandlerContext ctx) throws Exception {
            log.info("地址：{}连接了", ctx.channel().remoteAddress());
            ctx.channel().attr(KilsmeEncoder.SERIALIZE_KEY).set(providerProperties.getSerialize());
            ctx.channel().attr(KilsmeEncoder.SERIALIZE_MANAGER_KEY).set(serializerManager);

            ctx.channel().attr(KilsmeEncoder.COMPRESS_KEY).set(providerProperties.getCompress());
            ctx.channel().attr(KilsmeEncoder.COMPRESS_MANAGER_KEY).set(compressionManager);
            ctx.fireChannelActive();
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
            //链路发生异常
            log.error("发生了异常", cause);
            ctx.channel().close();
        }

        @Override
        public void channelInactive(ChannelHandlerContext ctx) throws Exception {
            log.info("地址：{}断开了", ctx.channel().remoteAddress());
            ctx.fireChannelInactive();
        }
    }

    private class FastFailResponseHandler implements RejectedExecutionHandler {

        @Override
        public void rejectedExecution(Runnable task, ThreadPoolExecutor executor) {
               if(task instanceof InvokeTask invokeTask){
                   Response fail = Response.fail("provider线程池满了，无法处理请求", invokeTask.request.getRequestId());
                   invokeTask.channelHandlerContext.write(fail);
                   return;
               }
               throw  new RuntimeException("task提交类型报错");
        }
    }

    private class InvokeTask implements Runnable {
        Request request;
        ChannelHandlerContext channelHandlerContext;
        ProviderRegistry.invocation<?> invocation;

        public InvokeTask(Request request, ChannelHandlerContext channelHandlerContext, ProviderRegistry.invocation<?> invocation) {
            this.channelHandlerContext = channelHandlerContext;
            this.request = request;
            this.invocation = invocation;

        }

        @Override
        public void run() {
            EventLoop eventLoop = channelHandlerContext.channel().eventLoop();
            try {
                long startTime = System.currentTimeMillis();
                // 真正调用接口实现方法。
                // 参数类型必须对上，否则反射会找不到方法或出现参数不匹配异常。
                Object result = invocation.invoke(request.getMethodName(), request.getParamsClass(), request.getParams());
                log.info("{}函数被远程调用了{}，结果是{},requestId{},耗时是{}", request.getServiceName(), request.getMethodName(), result,
                        request.getRequestId(), System.currentTimeMillis() - startTime);
                eventLoop.execute(() -> channelHandlerContext.writeAndFlush(Response.success(result, request.getRequestId())));
            } catch (Exception e) {
                eventLoop.execute(() -> {
                    Response failResp = Response.fail(String.format("%s.%s 调用失败: %s", request.getServiceName(), request.getMethodName(), e.getMessage()), request.getRequestId());
                    channelHandlerContext.writeAndFlush(failResp);
                });
            }
            // 这里输出原始请求对象，方便调试时查看请求是否完整到达 Provider。
            System.out.println(request);
        }
    }

    // 关闭 Provider 线程组。
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
