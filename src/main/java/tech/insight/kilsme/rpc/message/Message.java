package tech.insight.kilsme.rpc.message;

import lombok.Data;

import java.nio.charset.StandardCharsets;

/**
 * RPC 协议消息基础定义。
 *
 * <p>这个类更像是“协议头”的公共部分，真正的业务数据会放在 `Request` / `Response` 的 body 中。</p>
 */
@Data
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
    // 消息类型码：请求与响应在同一条 TCP 连接中通过 type 区分。
    public enum MessageType{
        // Consumer 发往 Provider 的调用请求。
        REQUEST(1),
        // Provider 返回给 Consumer 的调用结果。
        RESPONSE(2);

        private final byte code;
        MessageType(int code){
            this.code=(byte) code;
        }

        // 返回协议里实际写入的类型码。
        public byte getCode(){
            return code;
        }
    }
}
