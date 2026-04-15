package tech.insight.kilsme.rpc.retry;

import tech.insight.kilsme.rpc.exception.RpcException;
import tech.insight.kilsme.rpc.message.Response;
import tech.insight.kilsme.rpc.register.ServiceMetadata;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

public class FailoverRetryPolicy implements RetryPolicy {
    @Override
    public Response retry(RetryContext retryContext) throws Exception {
        //向其他provider进行发送请求
        List<ServiceMetadata> serviceMetadataList = new ArrayList<>(retryContext.getServiceMetadataList());
        if (serviceMetadataList.isEmpty()) {
            throw new RpcException("没有重试的provider");
        }
        serviceMetadataList.remove(retryContext.getFailService());
        ServiceMetadata failoverService = retryContext.getLoadBalancer().select(serviceMetadataList);
        CompletableFuture<Response> future = retryContext.doRpc(failoverService);
        return future.get(Math.min(retryContext.getRequestTimeoutMs(), retryContext.getMethodTimeoutMs()), TimeUnit.MILLISECONDS);

    }
}
