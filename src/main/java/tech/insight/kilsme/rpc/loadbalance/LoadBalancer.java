package tech.insight.kilsme.rpc.loadbalance;

import tech.insight.kilsme.rpc.register.ServiceMetadata;

import java.util.List;

/**
 * 负载均衡策略接口。
 * <p>
 * Consumer 在每次发起 RPC 调用前，会先从注册中心获取同一服务名下的多个 Provider 实例，
 * 再通过该接口选择其中一个目标实例进行请求发送。
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
