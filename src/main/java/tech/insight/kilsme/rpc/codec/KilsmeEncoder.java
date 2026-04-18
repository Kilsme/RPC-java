package tech.insight.kilsme.rpc.codec;

import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToByteEncoder;
import io.netty.util.AttributeKey;
import tech.insight.kilsme.rpc.message.Message;
import tech.insight.kilsme.rpc.serialize.Serializer;
import tech.insight.kilsme.rpc.serialize.SerializerManager;
import tech.insight.kilsme.rpc.version.Version;

import javax.naming.directory.Attribute;

public class KilsmeEncoder extends MessageToByteEncoder<Object> {
    public static final AttributeKey<Integer> SERIALIZE_KEY = AttributeKey.valueOf("serializeKey");
    public static final AttributeKey<SerializerManager> SERIALIZE_MANGER_KEY = AttributeKey.valueOf("serializeMangerKey");

    @Override
    protected void encode(io.netty.channel.ChannelHandlerContext ctx, Object msg, io.netty.buffer.ByteBuf out) throws Exception {
        Message.MessageType messageType = Message.MessageType.ofClass(msg.getClass());
        if (messageType == null) {
            throw new IllegalArgumentException("不支持的消息类型: " + msg.getClass());
        }
        byte[] magic = Message.MAGIC;
        byte messageCode = messageType.getCode();
        Version vesion = Version.v1;
        Serializer  defaultSerializer=getDefaultSerializer(ctx);
        if(defaultSerializer==null){
            throw  new IllegalArgumentException("不存在默认的序列化器");
        }
        byte[] body = defaultSerializer.serialize(msg);
        // length 不包含自身 4 字节，只表示后续载荷长度。
        int len = magic.length + Byte.BYTES *2+Short.BYTES+ body.length;
        // 按协议顺序写出，确保对端解码顺序一致。
        out.writeInt(len);
        out.writeBytes(magic);
        out.writeByte(messageCode);
        out.writeShort(vesion.getVersionNum());
        //写出序列化和压缩算法
        out.writeBytes(body);

    }

    private Serializer getDefaultSerializer(ChannelHandlerContext context) {
        SerializerManager serializerManager = context.channel().attr(SERIALIZE_MANGER_KEY).get();
        Integer serializeCode = context.channel().attr(SERIALIZE_KEY).get();
        return serializerManager.getSerializer(serializeCode);
    }
}
