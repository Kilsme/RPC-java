package tech.insight.kilsme.rpc.codec;

import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToByteEncoder;
import io.netty.util.AttributeKey;
import tech.insight.kilsme.rpc.compress.Compression;
import tech.insight.kilsme.rpc.compress.CompressionManager;
import tech.insight.kilsme.rpc.message.Message;
import tech.insight.kilsme.rpc.serialize.Serializer;
import tech.insight.kilsme.rpc.serialize.SerializerManager;
import tech.insight.kilsme.rpc.version.Version;

public class KilsmeEncoder extends MessageToByteEncoder<Object> {
    public static final AttributeKey<String> SERIALIZE_KEY = AttributeKey.valueOf("serializeKey");
    public static final AttributeKey<SerializerManager> SERIALIZE_MANAGER_KEY = AttributeKey.valueOf("serializeManagerKey");
    public static final AttributeKey<String> COMPRESS_KEY = AttributeKey.valueOf("compressKey");
    public static final AttributeKey<CompressionManager> COMPRESS_MANAGER_KEY =
            AttributeKey.valueOf("compressManagerKey");
    private volatile byte defaultSerializerAndCompression;
    private volatile Compression defaultCompression;
    private volatile Serializer defaultSerializer;

    @Override
    protected void encode(io.netty.channel.ChannelHandlerContext ctx, Object msg, io.netty.buffer.ByteBuf out) throws Exception {
        Message.MessageType messageType = Message.MessageType.ofClass(msg.getClass());
        initIfNecessary(ctx);
        if (messageType == null) {
            throw new IllegalArgumentException("不支持的消息类型: " + msg.getClass());
        }
        byte[] magic = Message.MAGIC;
        byte messageCode = messageType.getCode();
        Version vesion = Version.v1;
        if (defaultCompression == null) {
            throw new IllegalArgumentException("不存在默认的压缩器");
        }
        byte[] body = defaultSerializer.serialize(msg);
        byte finalSac=defaultSerializerAndCompression;
        if(body.length<256){
            finalSac&=(byte) 0b11110000;
        }{
            body = defaultCompression.compress(body);
        }
        // length 不包含自身 4 字节，只表示后续载荷长度。
        int len = magic.length + Byte.BYTES * 2 + Short.BYTES + body.length;
        // 按协议顺序写出，确保对端解码顺序一致。
        out.writeInt(len);
        out.writeBytes(magic);
        out.writeByte(messageCode);
        out.writeShort(vesion.getVersionNum());
        //写出序列化和压缩算法
        out.writeByte(finalSac);
        out.writeBytes(body);

    }

    private void initIfNecessary(ChannelHandlerContext context) {
        if (defaultSerializer != null) {
            return;
        }
        SerializerManager serializerManager = context.channel().attr(SERIALIZE_MANAGER_KEY).get();
       String serializeKey = context.channel().attr(SERIALIZE_KEY).get();
        defaultSerializer= serializerManager.getSerializer(serializeKey);
        CompressionManager CompressionManager = context.channel().attr(COMPRESS_MANAGER_KEY).get();
        String CompressionKey = context.channel().attr(COMPRESS_KEY).get();
        defaultCompression = CompressionManager.getCompression(CompressionKey);
        if (defaultCompression == null) {
            throw new IllegalArgumentException("不存在默认的压缩器");
        }
        if (defaultSerializer == null) {
            throw new IllegalArgumentException("不存在默认的序列化器");
        }
        defaultSerializerAndCompression = (byte) ((defaultSerializer.code() << 4) | defaultCompression.code());
    }
}
