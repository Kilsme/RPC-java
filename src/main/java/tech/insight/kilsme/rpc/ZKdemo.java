package tech.insight.kilsme.rpc;

import lombok.Data;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.retry.ExponentialBackoffRetry;
import org.apache.curator.x.discovery.ServiceDiscovery;
import org.apache.curator.x.discovery.ServiceDiscoveryBuilder;
import org.apache.curator.x.discovery.ServiceInstance;
import org.apache.curator.x.discovery.details.JsonInstanceSerializer;

public class ZKdemo {
    public static void main(String[] args) throws Exception {
        CuratorFramework client = CuratorFrameworkFactory.builder()
                .connectString("localhost:2181")
                .sessionTimeoutMs(5000)
                .connectionTimeoutMs(5000)
                .retryPolicy(new ExponentialBackoffRetry(1000, 3))
                .build();
        client.start();
        ServiceDiscovery<Metadata> discovery = ServiceDiscoveryBuilder.builder(Metadata.class)
                .client(client)
                .basePath("/Kilsme")
                .serializer(new JsonInstanceSerializer<>(Metadata.class))
                .build();
        discovery.start();
       Metadata metadata=new Metadata();
       metadata.setAge(18);
       metadata.setName("张三");
        ServiceInstance<Metadata> instance = ServiceInstance.<Metadata>builder().name("ZHANG")
                .address("127.0.0.1")
                .port(8888)
                .payload(metadata)
                .build();
        discovery.registerService(instance);
    }
    @Data
    public static class Metadata{
        private String name;
        private int age;
    }


}
