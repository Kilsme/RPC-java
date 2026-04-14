package tech.insight.kilsme.rpc.loadbalance;

import tech.insight.kilsme.rpc.register.ServiceMetadata;

import java.util.List;

public interface LoadBalancer {
    ServiceMetadata select(List<ServiceMetadata> serviceMetadataList);
}
