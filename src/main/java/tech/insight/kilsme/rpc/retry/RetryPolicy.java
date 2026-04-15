package tech.insight.kilsme.rpc.retry;

import tech.insight.kilsme.rpc.message.Response;

/**
 * 重试策略接口：定义调用失败后的补救行为。
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
