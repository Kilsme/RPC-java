package tech.insight.kilsme.rpc.codec;

/**
 * 响应编码器。
 * <p>
 * 将 Response 对象编码为 RPC 协议字节流，
 * 由 Provider 写回 Consumer，供客户端完成请求-响应匹配。
 * </p>
 */

import com.alibaba.fastjson2.JSONObject;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToByteEncoder;
import tech.insight.kilsme.rpc.message.Message;
import tech.insight.kilsme.rpc.message.Response;

import java.nio.charset.StandardCharsets;

/**
 * 响应编码器：把 Response 对象编码为 RPC 协议字节流。
 *
 * <p>Provider 执行完业务后，会把结果封装成 Response，再由这个编码器写回到 TCP 流中。</p>
 */
public class ResponseEncoder extends MessageToByteEncoder<Response> {
    @Override
    protected void encode(ChannelHandlerContext channelHandlerContext, Response response, ByteBuf byteBuf) throws Exception {
        // 协议格式：length(4) + logic(魔数) + type(1) + body(JSON)。
        // 与 RequestEncoder 保持一致，避免 Consumer 无法正确解码。
        byte[] logic = Message.MAGIC;
        byte messageType = Message.MessageType.RESPONSE.getCode();
        byte[] body = serializeResponse(response);
        // length 不包含自身 4 字节，只表示后续载荷长度。
        int len=logic.length+Byte.BYTES+body.length;
        // 按统一协议顺序写入，供 KilsmeDecoder 反向解析。
        byteBuf.writeInt(len);
        byteBuf.writeBytes(logic);
        byteBuf.writeByte(messageType);
        byteBuf.writeBytes(body);
    }
    // Response -> UTF-8 JSON 字节数组。
    private byte[]serializeResponse(Response response){
        return JSONObject.toJSONString(response).getBytes(StandardCharsets.UTF_8);
    }
}
