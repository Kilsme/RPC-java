package tech.insight.kilsme.rpc.handler;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.util.AttributeKey;

import java.util.concurrent.atomic.AtomicLong;

public class TrafficRecordHandler extends ChannelDuplexHandler {//双向处理器
    public static final AttributeKey<TrafficRecord> TRAFFIC_RECORD_KEY = AttributeKey.valueOf("traffic_record");
    private TrafficRecord trafficRecord;

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        if (msg instanceof ByteBuf byteBuf) {
            trafficRecord.download.getAndAdd(byteBuf.readableBytes());
        }
        ctx.fireChannelRead(msg);
    }

    @Override
    public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
        if (msg instanceof ByteBuf byteBuf) {
            trafficRecord.upload.getAndAdd(byteBuf.readableBytes());
        }
        ctx.write(msg, promise);

    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        trafficRecord = new TrafficRecord();
        //连接成功进行初始化
        ctx.channel().eventLoop().scheduleAtFixedRate(() -> {
            long upload = trafficRecord.upload.getAndSet(0);
            long download = trafficRecord.download.getAndSet(0);
            System.out.println("当前连接流量统计：上传 " + upload + " 字节，下载 " + download + " 字节");
        }, 5, 5, java.util.concurrent.TimeUnit.SECONDS);
        ctx.channel().attr(TRAFFIC_RECORD_KEY).set(trafficRecord);
        ctx.fireChannelActive();
    }


    public static class TrafficRecord {
        AtomicLong upload = new AtomicLong();
        AtomicLong download = new AtomicLong();
    }
}
