package tech.insight.kilsme.rpc.consumser;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.timeout.IdleStateHandler;
import lombok.extern.slf4j.Slf4j;
import tech.insight.kilsme.rpc.handler.HeartbeatHandler;
import tech.insight.kilsme.rpc.codec.KilsmeDecoder;
import tech.insight.kilsme.rpc.codec.KilsmeEncoder;
import tech.insight.kilsme.rpc.compress.Compression;
import tech.insight.kilsme.rpc.compress.CompressionManager;
import tech.insight.kilsme.rpc.handler.TrafficRecordHandler;
import tech.insight.kilsme.rpc.message.Response;
import tech.insight.kilsme.rpc.register.ServiceMetadata;
import tech.insight.kilsme.rpc.serialize.Serializer;
import tech.insight.kilsme.rpc.serialize.SerializerManager;

import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Consumer 连接管理器。
 * <p>
 * 负责按 Provider 维度复用 Netty Channel，避免每次请求都重新建连，
 * 并在连接断开时清理缓存、触发重连或失败处理。
 * </p>
 */
@Slf4j
public  class ConnectionManager {
    // 连接缓存：key=host:port，value=可复用 channel 包装。
    // 这里的 key 应该是服务地址，而不是简单的业务名，因为真正建立连接的是具体网络地址。
    private final Map<String, ChannelWrapper> channelTable = new ConcurrentHashMap<>();
    private final Bootstrap bootstrap;
    InFlightRequestManager inFlightRequestManager;
    private final ConsumerProperties consumerProperties;
    private final SerializerManager serializerManager;
    private final CompressionManager compressionManager;
    // 注入统一 Bootstrap，保证连接参数一致。
    public ConnectionManager(InFlightRequestManager inFlightRequestManager,
                             ConsumerProperties consumerProperties) {
        this.inFlightRequestManager=inFlightRequestManager;
        this.consumerProperties=consumerProperties;
        this.bootstrap = crateBootstrap(consumerProperties);
        this.serializerManager =new SerializerManager();
        this.compressionManager=new CompressionManager();
    }

    /**
     * 获取可用连接：优先复用，失效时由后续调用触发重建。
     *
     * host Provider 地址
     * port Provider 端口
     * @return 可用 Channel；连接失败时返回 null
     */
    public Channel getChannel(ServiceMetadata serviceMetadata) {
        String host=serviceMetadata.getHost();
        int port = serviceMetadata.getPort();
        String key = host + ":" + port;
        // 当缓存中没有连接时，创建新连接并放入表中。
        // 这样同一个 Provider 的后续调用就不用每次重新 connect。
        ChannelWrapper channelWrapper = channelTable.computeIfAbsent(key, k -> {
            try {
                ChannelFuture channelFuture = bootstrap.connect(host, port).sync();
                Channel channel = channelFuture.channel();
                // 连接关闭后自动清理缓存，避免复用失效连接。
                // 否则后续会从缓存拿到“看起来存在、实际上已经断开”的 Channel。
                channel.closeFuture().addListener((f) ->
                {
                    channelTable.remove(key);
                    inFlightRequestManager.cleatChannel(serviceMetadata);

                } );
                return new ChannelWrapper(channel);
            } catch (InterruptedException e) {
                log.info("连接超时{},{}", host, port, e);
                return new ChannelWrapper(null);
            }
        });
        Channel channel=channelWrapper.channel;
        // 兜底：连接为空或失活时移除缓存，让下一次调用重建连接。
        // 这样可以保证 getChannel() 返回的对象尽量都是可用连接。
        if(channel==null||!channel.isActive()){
            channelTable.remove(key);
        }
        return channel;
    }
    // 创建客户端 Bootstrap，并配置编解码与响应处理器。
    private Bootstrap crateBootstrap(ConsumerProperties consumerProperties) {
        // Bootstrap 对应“客户端连接配置”。
        // 它负责定义客户端如何创建连接、使用什么线程组、pipeline 里放哪些 handler。
        Bootstrap bootstrap = new Bootstrap();
        bootstrap.group(new NioEventLoopGroup(consumerProperties.getWorkThreadNum()))
                .channel(NioSocketChannel.class)
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, consumerProperties.getConnectTimeoutMs())
                // 客户端只有一个连接，因此使用 handler 初始化该连接的 pipeline。
                .handler(new ChannelInitializer<NioSocketChannel>() {
                    @Override
                    protected void initChannel(NioSocketChannel nioSocketChannel) throws Exception {
                        // 入站：先按协议解码，再交给业务 handler 处理 Response。
                        // 出站：RequestEncoder 在 writeAndFlush(Request) 时自动生效。
                        // 这里的顺序非常重要：先解码，后处理业务，否则拿到的只是原始字节流。
                        nioSocketChannel.pipeline()
                                .addLast(new TrafficRecordHandler())
                                .addLast(new KilsmeDecoder())
                                .addLast(new KilsmeEncoder())
                                // 业务入站处理器：收到响应后完成 Future，并关闭连接。
                                .addLast(new IdleStateHandler(30,5,0, TimeUnit.SECONDS))
                                .addLast(new HeartbeatHandler())
                                .addLast(new ConsumerHandler());
                    }
                });
        return bootstrap;
    }
    /**
     * 客户端入站处理器：负责把 Provider 返回的响应交给 in-flight 管理器。
     *
     * <p>这个 handler 的职责很单一：收到 Response 后，根据 requestId 找到对应 Future，
     * 然后 complete 它，唤醒正在等待结果的业务线程。</p>
     */
    private class ConsumerHandler extends SimpleChannelInboundHandler<Response> {
        @Override
        protected void channelRead0(ChannelHandlerContext channelHandlerContext, Response response) throws Exception {
            // 用 requestId 找到对应 Future 并完成，唤醒正在 get() 的业务线程。
            // 这里是“响应 -> 请求”的反向关联点。
            inFlightRequestManager.completeRequest(response.getRequestId(), response);
        }

        @Override
        public void channelActive(ChannelHandlerContext ctx) throws Exception {
            log.info("地址：{}连接了", ctx.channel().remoteAddress());
            Serializer.SerializerType serializerType = Serializer.SerializerType.valueOf(consumerProperties.getSerialize().toUpperCase(Locale.ROOT));
            ctx.channel().attr(KilsmeEncoder.SERIALIZE_KEY).set(serializerType.getTypeCode());
            ctx.channel().attr(KilsmeEncoder.SERIALIZE_MANAGER_KEY).set(serializerManager);
            Compression.CompressionType compressionType = Compression.CompressionType.valueOf(consumerProperties.getCompress().toUpperCase(Locale.ROOT));
            ctx.channel().attr(KilsmeEncoder.COMPRESS_KEY).set(compressionType.getTypeCode());
            ctx.channel().attr(KilsmeEncoder.COMPRESS_MANAGER_KEY).set(compressionManager);
            ctx.fireChannelActive();

        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
            // 链路发生异常：比如编解码异常、连接异常、远端主动关闭等。
            log.error("发生了异常", cause);
            ctx.channel().close();
        }

        @Override
        public void channelInactive(ChannelHandlerContext ctx) throws Exception {
            log.info("地址：{}断开了", ctx.channel().remoteAddress());
            ctx.fireChannelInactive();
        }

    }


    // 轻量包装，后续可扩展连接状态/创建时间/失败计数等信息。
    // 现在先只包一层 Channel，方便以后给连接加元数据。
    private static class ChannelWrapper {
        final Channel channel;
        private ChannelWrapper(Channel channel) {
            this.channel = channel;
        }
    }

}
