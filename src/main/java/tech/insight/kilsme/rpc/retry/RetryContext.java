package tech.insight.kilsme.rpc.retry;

import lombok.Data;
import tech.insight.kilsme.rpc.loadbalance.LoadBalancer;
import tech.insight.kilsme.rpc.message.Response;
import tech.insight.kilsme.rpc.register.ServiceMetadata;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

/**
 * 重试上下文。
 * <p>
 * 保存一次调用在重试过程中的状态信息（已重试次数、最后异常等），
 * 供 RetryPolicy 在每次判定时使用。
 * </p>
 *
 * @see RetryPolicy
 */
@Data
public class RetryContext {
    // 首次调用失败的 Provider。
    private ServiceMetadata failService;
    // 当前服务对应的可用 Provider 列表。
    private List<ServiceMetadata> serviceMetadataList;
    // 方法级总超时预算（包含重试过程）。
    private long methodTimeoutMs;
    // 单次 RPC 请求超时。
    private long requestTimeoutMs;
    // 负载均衡策略，用于在候选节点中选目标节点。
    private LoadBalancer loadBalancer;
    // 发起一次异步 RPC 的函数式入口。
    private Function<ServiceMetadata, CompletableFuture<Response>> dorpcFunction;//参数和返回值

    // 对指定 Provider 发起一次 RPC 调用。
    public CompletableFuture<Response> doRpc(ServiceMetadata serviceMetadata) {
        return dorpcFunction.apply(serviceMetadata);
    }


}
