package tech.insight.kilsme.rpc.exception;

/**
 * 限流相关业务异常。
 * <p>
 * 当请求触发速率限制或并发限制时抛出该异常，
 * 用于向调用方明确区分“系统错误”与“流控拒绝”。
 */
public class LimitException extends  RpcException{
    public LimitException(String message) {
        super(message);
    }
}
