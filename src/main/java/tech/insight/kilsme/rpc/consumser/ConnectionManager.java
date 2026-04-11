package tech.insight.kilsme.rpc.consumser;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
public  class ConnectionManager {
    private final Map<String, ChannelWrapper> channelTable = new ConcurrentHashMap<>();
    private final Bootstrap bootstrap;

    public ConnectionManager(Bootstrap bootstrap) {
        this.bootstrap = bootstrap;
    }

    public Channel getChannel(String host, int port) {
        String key = host + ":" + port;
        //当这个key不存在的时候直接存进去
        ChannelWrapper channelWrapper = channelTable.computeIfAbsent("key", k -> {
            try {
                ChannelFuture channelFuture = bootstrap.connect(host, port).sync();
                Channel channel = channelFuture.channel();
                //进行增加监听器 当连接失败时自动的删除这个key
                channel.closeFuture().addListener((f) ->
                        channelTable.remove(key));
                return new ChannelWrapper(channel);
            } catch (InterruptedException e) {
                log.info("连接超时{},{}", host, port, e);
                return new ChannelWrapper(null);
            }
        });
        Channel channel=channelWrapper.channel;
        if(channel==null||!channel.isActive()){
            channelTable.remove(key);
        }
        return channel;
    }

    //定义一个包装的内部类
    private static class ChannelWrapper {
        final Channel channel;

        private ChannelWrapper(Channel channel) {
            this.channel = channel;
        }
    }
}
