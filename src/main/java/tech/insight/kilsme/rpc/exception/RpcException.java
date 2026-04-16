package tech.insight.kilsme.rpc.exception;

/**
 * RPC 业务异常：用于表示远程调用失败、超时或协议级错误。
 */
public class RpcException extends RuntimeException {
    // 传入可读错误信息，直接向上抛给调用方。
    public RpcException(String message) {
        super(message);
    }

    /**
     * 是否允许重试。
     *
     * <p>默认返回 false；如果某些异常希望被重试策略继续处理，
     * 可以在子类中覆写该方法返回 true。
     */
    public boolean retry() {
        return false;
    }
}
