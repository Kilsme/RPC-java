package tech.insight.kilsme.rpc.retry;

import tech.insight.kilsme.rpc.message.Response;

public interface RetryPolicy {
    Response retry(RetryContext retryContext)throws Exception;
}
