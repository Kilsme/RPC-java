package tech.insight.kilsme.rpc.codec;

import com.alibaba.fastjson2.JSONObject;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToByteEncoder;
import tech.insight.kilsme.rpc.message.Message;
import tech.insight.kilsme.rpc.message.Request;

import java.nio.charset.StandardCharsets;

/**
 * 请求编码器。
 * <p>
 * 将 Request 对象编码为二进制协议：长度字段 + 魔数 + 消息类型 + JSON body。
 * 该编码结果会通过 Netty 出站链路发送给 Provider。
 * </p>
 */
public class RequestEncoder extends MessageToByteEncoder<Request> {
    @Override
    protected void encode(ChannelHandlerContext channelHandlerContext, Request request, ByteBuf byteBuf) throws Exception {
        // 协议格式：length(4) + magic(魔数) + type(1) + body(JSON)。
        // 这个顺序必须和 KilsmeDecoder 的读取顺序完全一致。
        byte[] magic = Message.MAGIC;
        byte messageType = Message.MessageType.REQUEST.getCode();
        byte[] body = serializeRequest(request);
        // length 不包含自身 4 字节，只表示后续载荷长度。
        int len= magic.length+Byte.BYTES+body.length;
        // 按协议顺序写出，确保对端解码顺序一致。
        byteBuf.writeInt(len);
        byteBuf.writeBytes(magic);
        byteBuf.writeByte(messageType);
        byteBuf.writeBytes(body);
    }

    // Request -> UTF-8 JSON 字节数组。
    private byte[]serializeRequest(Request request){
        return JSONObject.toJSONString(request).getBytes(StandardCharsets.UTF_8);
    }

}
