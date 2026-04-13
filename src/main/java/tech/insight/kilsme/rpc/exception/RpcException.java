package tech.insight.kilsme.rpc.exception;

/**
 * RPC 业务异常：用于表示远程调用失败、超时或协议级错误。
 */
public class RpcException extends RuntimeException {
    // 传入可读错误信息，直接向上抛给调用方。
    public RpcException(String message) {
        super(message);
    }
}
