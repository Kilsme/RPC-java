package tech.insight.kilsme.rpc.loadbalance;

import tech.insight.kilsme.rpc.register.ServiceMetadata;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 轮询负载均衡实现。
 * <p>
 * 通过原子递增计数器在实例列表中顺序选择节点，
 * 能保证在稳定列表下请求分配相对均匀。
 */
public class RoundRobinLoadBalancer implements LoadBalancer{
    // 全局递增序号，保证并发下下标计算安全。
    private final AtomicInteger index= new AtomicInteger();

    @Override
    public ServiceMetadata select(List<ServiceMetadata> serviceMetadataList) {
        // 例如 size=3 时，下标序列为 0,1,2,0,1,2...
        int MetadataIndex = index.getAndIncrement() % serviceMetadataList.size();
        return serviceMetadataList.get(Math.abs(MetadataIndex));
    }
}
