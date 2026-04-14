package tech.insight.kilsme.rpc.loadbalance;

import tech.insight.kilsme.rpc.register.ServiceMetadata;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
//进行轮询的负载均衡器
public class RoundRobinLoadBalancer implements LoadBalancer{
    private final AtomicInteger index= new AtomicInteger();
    @Override
    public ServiceMetadata select(List<ServiceMetadata> serviceMetadataList) {
        int MetadataIndex = index.getAndIncrement() % serviceMetadataList.size();
        return serviceMetadataList.get(Math.abs(MetadataIndex));
    }
}
