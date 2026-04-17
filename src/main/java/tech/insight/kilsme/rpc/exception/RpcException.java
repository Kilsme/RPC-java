package tech.insight.kilsme.rpc.exception;

/**
 * RPC 统一运行时异常。
 * <p>
 * 用于封装远程调用过程中的各类失败场景（服务不存在、网络异常、序列化异常、超时等），
 * 使上层调用代码可以通过一个异常类型统一处理 RPC 错误。
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
