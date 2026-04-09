package tech.insight.kilsme.rpc.message;

import lombok.Data;

import java.nio.charset.StandardCharsets;
@Data
public class Message {
    // 协议魔数：用于快速识别是否为本 RPC 协议数据。
    public static  final byte[]LOGIC="杨杨".getBytes(StandardCharsets.UTF_8);
    // 以下字段描述一条完整协议消息结构。
    private byte[]logic;
    private byte messageType;
    private byte[]body;
    // 消息类型码：请求与响应在同一条 TCP 连接中通过 type 区分。
    public enum MessageType{
        REQUEST(1), RESPONSE(2);
        private final byte code;
        MessageType(int code){
            this.code=(byte) code;
        }
        public byte getCode(){
            return code;
        }
    }
}
