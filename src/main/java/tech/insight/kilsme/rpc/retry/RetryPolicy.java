package tech.insight.kilsme.rpc.retry;

import tech.insight.kilsme.rpc.message.Response;

/**
 * 重试策略抽象接口。
 * <p>
 * 定义当一次 RPC 调用失败时，是否重试、如何重试以及何时终止重试。
 * </p>
 */
public interface RetryPolicy {
    /**
     * 执行重试并返回最终响应。
     *
     * @param retryContext 重试所需上下文
     * @return 重试成功后的响应
     * @throws Exception 当重试最终失败时抛出
     */
    Response retry(RetryContext retryContext)throws Exception;
}
