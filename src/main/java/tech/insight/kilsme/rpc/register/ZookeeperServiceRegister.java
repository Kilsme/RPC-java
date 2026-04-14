package tech.insight.kilsme.rpc.register;
import lombok.extern.slf4j.Slf4j;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.retry.ExponentialBackoffRetry;
import org.apache.curator.x.discovery.ServiceDiscovery;
import org.apache.curator.x.discovery.ServiceDiscoveryBuilder;
import org.apache.curator.x.discovery.ServiceInstance;
import org.apache.curator.x.discovery.details.JsonInstanceSerializer;

import java.util.List;

/**
 * 基于 Curator ServiceDiscovery 的 Zookeeper 注册中心实现。
 */
@Slf4j
public class ZookeeperServiceRegister implements ServiceRegistry {
    // RPC 服务统一根路径。
    private static final String BASE_RPC = "/Kilsme/rpc";
    private CuratorFramework client;
    private ServiceDiscovery<ServiceMetadata> discovery;

    @Override
    public void init(RegistryConfig registerConfig) throws Exception {
        // 初始化 ZK 客户端，并启动 ServiceDiscovery。
        client = CuratorFrameworkFactory.builder()
                .connectString(registerConfig.getConnectString())
                .sessionTimeoutMs(5000)
                .connectionTimeoutMs(5000)
                .retryPolicy(new ExponentialBackoffRetry(1000, 3))
                .build();
        client.start();
        discovery = ServiceDiscoveryBuilder.builder(ServiceMetadata.class)
                .client(client)
                .basePath(BASE_RPC)
                .serializer(new JsonInstanceSerializer<>(ServiceMetadata.class))
                .build();
        discovery.start();
    }

    @Override
    public void registerService(ServiceMetadata metadata) {
        try {
            // 把服务元数据包装为 ServiceInstance 写入 ZK。
            ServiceInstance<ServiceMetadata> instance = ServiceInstance
                    .<ServiceMetadata>builder()
                    .address(metadata.getHost())
                    .port(metadata.getPort())
                    .name(metadata.getServiceName())
                    .payload(metadata)
                    .build();
            discovery.registerService(instance);
        } catch (Exception e) {
            log.error("{}注册失败", metadata, e);
            throw new RuntimeException("服务注册失败", e);

        }
    }

    @Override
    public List<ServiceMetadata> fetchServiceList(String serviceName) throws Exception {
        // 直接查询 serviceName 下所有实例，交由上层做负载均衡。
        return discovery.queryForInstances(serviceName).stream().map(ServiceInstance::getPayload).toList();
        //Collection<ServiceInstance<ServiceMetadata>> serviceInstances = discovery.queryForInstances(serviceName);
    }
}
