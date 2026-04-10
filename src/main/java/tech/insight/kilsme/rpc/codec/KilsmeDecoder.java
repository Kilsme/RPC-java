package tech.insight.kilsme.rpc.codec;
import com.alibaba.fastjson2.JSONObject;
import com.alibaba.fastjson2.JSONReader;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;
import tech.insight.kilsme.rpc.message.Message;
import tech.insight.kilsme.rpc.message.Request;
import tech.insight.kilsme.rpc.message.Response;

import java.util.Arrays;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

/**
 * 协议解码器：把字节流反序列化为 Request/Response。
 */
public class KilsmeDecoder extends LengthFieldBasedFrameDecoder {
    public KilsmeDecoder(){
        // 基于长度字段解码：
        // maxFrameLength=1MB，lengthFieldOffset=0，lengthFieldLength=4，
        // lengthAdjustment=0，initialBytesToStrip=4（去掉长度字段本身）。
        super(1024*1024,0,Integer.BYTES,0,Integer.BYTES);
    }

    @Override
    protected Object decode(ChannelHandlerContext ctx, ByteBuf in) throws Exception {
        // 先按长度字段切出一帧完整业务报文，避免粘包/半包。
        ByteBuf frame = (ByteBuf) super.decode(ctx, in);
        if (frame == null) {
            // 数据还不完整，等待下次网络数据到达。
            return null;
        }
        try {
            // 1) 校验魔数，确认是我们定义的协议。
            byte[] logic = new byte[Message.LOGIC.length];
            frame.readBytes(logic);
            if(!Arrays.equals(logic,Message.LOGIC)){
                throw new IllegalAccessException("魔术不对");
            }
            // 2) 读取消息类型（请求/响应）。
            byte messageType = frame.readByte();
            // 3) 剩余部分全部作为 JSON body。
            byte[] body = new byte[frame.readableBytes()];
            frame.readBytes(body);
            // 4) 按消息类型反序列化为具体对象。
            if(Objects.equals(Message.MessageType.REQUEST.getCode(),messageType)){
                return deserializeRequest(body);
            }
            if(Objects.equals(Message.MessageType.RESPONSE.getCode(),messageType)){
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

    // 将响应 JSON 转换为 Response。
    private Response deserializeResponse(byte[]body){
        // 响应体采用 UTF-8 JSON 编码。
        String json = new String(body, StandardCharsets.UTF_8);
        return JSONObject.parseObject(json, Response.class);
    }


}

