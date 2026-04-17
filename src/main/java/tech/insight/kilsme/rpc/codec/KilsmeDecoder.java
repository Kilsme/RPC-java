package tech.insight.kilsme.rpc.codec;

import com.alibaba.fastjson2.JSONObject;
import com.alibaba.fastjson2.JSONReader;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;
import tech.insight.kilsme.rpc.message.Message;
import tech.insight.kilsme.rpc.message.Request;
import tech.insight.kilsme.rpc.message.Response;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Objects;

/**
 * RPC 协议解码器。
 * <p>
 * 负责把网络字节流解码为 Message/Request/Response 对象，
 * 是 Netty 入站链路中“字节 -> 业务对象”的关键环节。
 * </p>
 *
 * <p>它负责把网络中的二进制数据还原成 Java 对象，是 RPC 协议进入业务逻辑前的第一道关卡。</p>
 */
public class KilsmeDecoder extends LengthFieldBasedFrameDecoder {
    public KilsmeDecoder(){
        // 基于长度字段解码：
        // maxFrameLength=1MB，lengthFieldOffset=0，lengthFieldLength=4，
        // lengthAdjustment=0，initialBytesToStrip=4（去掉长度字段本身）。
        super(1024*1024,0,Integer.BYTES,0,Integer.BYTES);
    }

    /*
    题外话 ByteBuf使用的是引用计数的过程因为netty为了性能考虑，使用了池化内存分配和引用计数机制来管理内存
    。每当一个 ByteBuf 被创建时，它的引用计数被设置为 1。当这个 ByteBuf 被传递给下一个 handler 时，
    Netty 会增加它的引用计数，以确保它在被多个 handler 使用时不会被提前释放。
    当一个 handler 完成对 ByteBuf 的处理后，它需要调用 release() 方法来减少引用计数。
    当引用计数降到 0 时，Netty 会自动回收这个 ByteBuf 的内存。这种机制可以有效地避免内存泄漏和过早释放的问题
    ，同时也提高了性能，因为它减少了垃圾回收的压力。
    但是jvm中采用的是可达性分析（jvm中对象可以出现循环依赖如果使用引用计数的话计数不会到0）
     */
    @Override
    protected Object decode(ChannelHandlerContext ctx, ByteBuf in) throws Exception {
        // 先按长度字段切出一帧完整业务报文，避免粘包/半包。
        ByteBuf frame = (ByteBuf) super.decode(ctx, in);
        //head-->handler1-->handler2 -->tail pipeline  byteBuf中指向的是计算机的直接内存
        if (frame == null) {
            // 数据还不完整，等待下次网络数据到达。
            return null;
        }
        try {
            // 1) 校验魔数，确认是我们定义的协议。
            // 如果魔数不对，说明对端发来的不是当前 RPC 框架的数据。
            byte[] logic = new byte[Message.MAGIC.length];
            frame.readBytes(logic);
            if(!Arrays.equals(logic,Message.MAGIC)){
                throw new IllegalAccessException("魔术不对");
            }
            // 2) 读取消息类型（请求/响应）。
            // 同一条连接上既可能传请求，也可能传响应，必须通过这个字段区分。
            byte messageType = frame.readByte();
            // 3) 剩余部分全部作为 JSON body。
            // body 里真正存的是 Request/Response 的字段内容。
            byte[] body = new byte[frame.readableBytes()];
            frame.readBytes(body);
            // 4) 按消息类型反序列化为具体对象。
            if(Objects.equals(Message.MessageType.REQUEST.getCode(),messageType)){
                // 解码为请求对象，交给 Provider 入站 handler。
                return deserializeRequest(body);
            }
            if(Objects.equals(Message.MessageType.RESPONSE.getCode(),messageType)){
                // 解码为响应对象，交给 Consumer 入站 handler。
                return deserializeResponse(body);
            }
            throw new IllegalAccessException("消息类型不支持" + messageType);
        } finally {
            // frame 由当前 handler 持有，使用完必须释放，避免内存泄漏。
            frame.release();
        }
    }

    // 将请求 JSON 转换为 Request，并开启 Class 名称反序列化支持。
    private Request deserializeRequest(byte[]body){
        // 请求体采用 UTF-8 JSON 编码。
        String json = new String(body, StandardCharsets.UTF_8);
        return JSONObject.parseObject(json, Request.class, JSONReader.Feature.SupportClassForName);
    }
    // 将响应 JSON 转换为 Response
    private Response deserializeResponse(byte[]body){
        // 响应体采用 UTF-8 JSON 编码。
        String json = new String(body, StandardCharsets.UTF_8);
        return JSONObject.parseObject(json, Response.class);
    }


}
