package tech.insight.kilsme.rpc.retry;

import tech.insight.kilsme.rpc.exception.RpcException;
import tech.insight.kilsme.rpc.message.Response;
import tech.insight.kilsme.rpc.register.ServiceMetadata;
import tech.insight.kilsme.rpc.spi.Spi;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Failover 重试策略。
 * <p>
 * 某实例调用失败后切换到其他实例继续重试，
 * 能提升整体成功率，是分布式 RPC 常见重试模型。
 */
@Spi("failover")
public class FailoverRetryPolicy implements RetryPolicy {
    @Override
    public Response retry(RetryContext retryContext) throws Exception {
        // 复制一份候选列表，避免直接修改原始数据。
        List<ServiceMetadata> serviceMetadataList = new ArrayList<>(retryContext.getServiceMetadataList());
        if (serviceMetadataList.isEmpty()) {
            throw new RpcException("没有重试的provider");
        }
        // 从候选中移除首个失败节点，避免立即打到同一故障节点。
        serviceMetadataList.remove(retryContext.getFailService());
        // 通过负载均衡策略重新挑选目标实例。
        ServiceMetadata failoverService = retryContext.getLoadBalancer().select(serviceMetadataList);
        CompletableFuture<Response> future = retryContext.doRpc(failoverService);
        // 受请求超时与方法总超时共同约束。
        return future.get(Math.min(retryContext.getRequestTimeoutMs(), retryContext.getMethodTimeoutMs()), TimeUnit.MILLISECONDS);
    }
}
