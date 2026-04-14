package tech.insight.kilsme.rpc.consumser;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Consumer 连接管理器：按 host:port 缓存并复用 Netty Channel。
 */
@Slf4j
public  class ConnectionManager {
    // 连接缓存：key=host:port，value=可复用 channel 包装。
    private final Map<String, ChannelWrapper> channelTable = new ConcurrentHashMap<>();
    private final Bootstrap bootstrap;

    // 注入统一 Bootstrap，保证连接参数一致。
    public ConnectionManager(Bootstrap bootstrap) {
        this.bootstrap = bootstrap;
    }

    // 获取可用连接：优先复用，失效时由后续调用触发重建。
    public Channel getChannel(String host, int port) {
        String key = host + ":" + port;
        // 当缓存中没有连接时，创建新连接并放入表中。
        ChannelWrapper channelWrapper = channelTable.computeIfAbsent("key", k -> {
            try {
                ChannelFuture channelFuture = bootstrap.connect(host, port).sync();
                Channel channel = channelFuture.channel();
                // 连接关闭后自动清理缓存，避免复用失效连接。
                channel.closeFuture().addListener((f) ->
                        channelTable.remove(key));
                return new ChannelWrapper(channel);
            } catch (InterruptedException e) {
                log.info("连接超时{},{}", host, port, e);
                return new ChannelWrapper(null);
            }
        });
        Channel channel=channelWrapper.channel;
        // 兜底：连接为空或失活时移除缓存，让下一次调用重建连接。
        if(channel==null||!channel.isActive()){
            channelTable.remove(key);
        }
        return channel;
    }
    // 轻量包装，便于后续扩展连接状态字段。
    private static class ChannelWrapper {
        final Channel channel;
        private ChannelWrapper(Channel channel) {
            this.channel = channel;
        }
    }
}
