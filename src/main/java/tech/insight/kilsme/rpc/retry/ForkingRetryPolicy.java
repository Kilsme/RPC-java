package tech.insight.kilsme.rpc.retry;

import tech.insight.kilsme.rpc.exception.RpcException;
import tech.insight.kilsme.rpc.message.Response;
import tech.insight.kilsme.rpc.register.ServiceMetadata;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Forking 重试策略：并发请求多个 Provider，取最先返回的结果。
 */
public class ForkingRetryPolicy implements RetryPolicy{
    @Override
    public Response retry(RetryContext retryContext) throws Exception {
        // 拷贝候选列表，作为并发请求目标集合。
        List<ServiceMetadata> serviceMetadataList = new ArrayList<>(retryContext.getServiceMetadataList());
        if (serviceMetadataList.isEmpty()) {
            throw new RpcException("没有重试的provider");
        }
        // 为每个 Provider 发起一次并发 RPC。
        CompletableFuture[]allFuture=new CompletableFuture[serviceMetadataList.size()];
        for(int i=0;i<allFuture.length;i++){
            allFuture[i]=retryContext.doRpc(serviceMetadataList.get(i));
        }
        // anyOf 返回最先完成（成功/失败）的那一个 Future。
        CompletableFuture<Object> mainFuture = CompletableFuture.anyOf(allFuture);
        // 最终等待时间由单次请求超时与方法剩余时间共同限制。
        return (Response) mainFuture.get(Math.min(retryContext.getRequestTimeoutMs(),retryContext.getMethodTimeoutMs()),TimeUnit.MILLISECONDS);
    }
}
