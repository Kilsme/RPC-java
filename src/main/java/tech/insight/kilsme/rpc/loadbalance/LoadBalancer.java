package tech.insight.kilsme.rpc.loadbalance;

import tech.insight.kilsme.rpc.register.ServiceMetadata;

import java.util.List;

/**
 * 负载均衡策略接口。
 * <p>
 * Consumer 在拿到同一服务的多个 Provider 实例后，
 * 通过该接口选择“本次请求要路由到哪一个实例”。
 */
public interface LoadBalancer {
    /**
     * 从候选 Provider 列表中选出一个目标实例。
     *
     * @param serviceMetadataList 同一服务对应的可用实例列表
     * @return 被选中的目标实例
     */
    ServiceMetadata select(List<ServiceMetadata> serviceMetadataList);
}
