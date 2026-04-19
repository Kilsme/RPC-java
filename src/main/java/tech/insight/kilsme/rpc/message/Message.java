package tech.insight.kilsme.rpc.message;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * RPC 传输层通用消息封装。
 * <p>
 * 该类通常用于定义消息头/消息类型等基础信息，
 * 作为 Request 与 Response 的公共抽象，便于编解码器统一处理。
 * </p>
 */
public class Message {
    // 协议魔数：用于快速识别是否为本 RPC 协议数据。
    // 任何不符合这个魔数的报文，都应该被认为不是当前 RPC 协议。
    public static  final byte[] MAGIC ="杨杨".getBytes(StandardCharsets.UTF_8);
    // magic 字段，实际承载 MAGIC。
    private byte[]magic;
    // 消息类型码：1=request, 2=response。
    // 同一条 TCP 连接上，Client/Server 都可能收发数据，所以必须靠 type 区分具体报文含义。
    private byte messageType;
    // 消息体，当前使用 JSON 字节数组。
    // 这里没有直接写 Java 对象，而是先转成字节数组，方便网络传输和跨语言扩展。
    private byte[]body;
    private short version;
    private byte serializeAndCompress;//序列化和压缩算法方式
    // 消息类型码：请求与响应在同一条 TCP 连接中通过 type 区分。
    public enum MessageType{
        // Consumer 发往 Provider 的调用请求。
        REQUEST(1,Request.class),
        // Provider 返回给 Consumer 的调用结果。
        RESPONSE(2,Response.class);
        private static final Map<Class<?>,MessageType>CLASS_CACHE=new HashMap<>();
        private static final Map<Byte,MessageType>CODE_CACHE=new HashMap<>();
        private final byte code;
        private final Class<?>messageClass;
        static{
            for(MessageType value:values()){
                if (CLASS_CACHE.put(value.messageClass,value)!=null) {
                    try {
                        throw new IllegalAccessException("vale没有对应的类型");
                    } catch (IllegalAccessException e) {
                        throw new RuntimeException(e);
                    }
                }
                if (CODE_CACHE.put(value.code,value)!=null) {
                    try {
                        throw new IllegalAccessException("vale没有对应的类型");
                    } catch (IllegalAccessException e) {
                        throw new RuntimeException(e);
                    }
                }
            }
        }
        MessageType(int code,Class<?>messageClass){
            this.code=(byte) code;
            this.messageClass=messageClass;
        }

        // 返回协议里实际写入的类型码。
        public byte getCode(){
            return code;
        }

        public Class<?> getMessageClass() {
            return messageClass;
        }
        public static MessageType ofClass(Class<?>messageClass){
            return CLASS_CACHE.get(messageClass);
        }
        public static MessageType ofCode(Byte messageCode){
            return CODE_CACHE.get(messageCode);
        }
    }
}
