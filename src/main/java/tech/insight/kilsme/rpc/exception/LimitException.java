package tech.insight.kilsme.rpc.exception;

/**
 * 限流异常。
 *
 * <p>当请求被全局限流或单连接限流拒绝时抛出该异常，
 * 上层可据此区分“系统保护性拒绝”与“业务执行失败”。
 */
public class LimitException extends  RpcException{
    public LimitException(String message) {
        super(message);
    }
}
