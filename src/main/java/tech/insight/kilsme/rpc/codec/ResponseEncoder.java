package tech.insight.kilsme.rpc.codec;

import com.alibaba.fastjson2.JSONObject;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToByteEncoder;
import tech.insight.kilsme.rpc.message.Message;
import tech.insight.kilsme.rpc.message.Response;

import java.nio.charset.StandardCharsets;

public class ResponseEncoder extends MessageToByteEncoder<Response> {
    @Override
    protected void encode(ChannelHandlerContext channelHandlerContext, Response response, ByteBuf byteBuf) throws Exception {
        // 协议格式：length(4) + logic(魔数) + type(1) + body(JSON)。
        byte[] logic = Message.LOGIC;
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
