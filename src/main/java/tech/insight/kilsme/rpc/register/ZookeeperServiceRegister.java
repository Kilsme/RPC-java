package tech.insight.kilsme.rpc.register;

import lombok.extern.slf4j.Slf4j;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.retry.ExponentialBackoffRetry;
import org.apache.curator.x.discovery.ServiceDiscovery;
import org.apache.curator.x.discovery.ServiceDiscoveryBuilder;
import org.apache.curator.x.discovery.ServiceInstance;
import org.apache.curator.x.discovery.details.JsonInstanceSerializer;
import tech.insight.kilsme.rpc.ZKdemo;

import java.security.Provider;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

@Slf4j
public class ZookeeperServiceRegister implements ServiceRegister {
    private static final String BASE_RPC = "/Kilsme/rpc";
    private CuratorFramework client;
    private ServiceDiscovery<ServiceMetadata> discovery;

    @Override
    public void init(RegisterConfig registerConfig) throws Exception {
        //进行Zookeeper连接的初始化，创建必要的节点结构等。
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
        //出现异常直接抛出
        return discovery.queryForInstances(serviceName).stream().map(ServiceInstance::getPayload).toList();
        //Collection<ServiceInstance<ServiceMetadata>> serviceInstances = discovery.queryForInstances(serviceName);

    }
}
