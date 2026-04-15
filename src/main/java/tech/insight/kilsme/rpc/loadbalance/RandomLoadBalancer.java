package tech.insight.kilsme.rpc.loadbalance;

import tech.insight.kilsme.rpc.register.ServiceMetadata;

import java.util.List;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;

public class RandomLoadBalancer implements LoadBalancer{
    private final Random random = new Random();
    @Override
    public ServiceMetadata select(List<ServiceMetadata> serviceMetadataList) {
        int MetadataIndex =random.nextInt(serviceMetadataList.size());
        return serviceMetadataList.get(Math.abs(MetadataIndex));
    }
}
