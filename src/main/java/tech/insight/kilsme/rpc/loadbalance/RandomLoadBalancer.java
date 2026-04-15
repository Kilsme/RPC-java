package tech.insight.kilsme.rpc.loadbalance;

import tech.insight.kilsme.rpc.register.ServiceMetadata;

import java.util.List;
import java.util.Random;

/**
 * 随机负载均衡：每次调用时随机选择一个 Provider。
 * <p>
 * 适合节点性能接近、请求量较大的场景，能在统计意义上实现较均匀分布。
 */
public class RandomLoadBalancer implements LoadBalancer{
    // 线程安全随机数生成器（Random 在此场景足够使用）。
    private final Random random = new Random();

    @Override
    public ServiceMetadata select(List<ServiceMetadata> serviceMetadataList) {
        // 生成 [0, size) 的随机下标。
        int MetadataIndex =random.nextInt(serviceMetadataList.size());
        // 取绝对值作为防御式写法，避免极端情况下负下标。
        return serviceMetadataList.get(Math.abs(MetadataIndex));
    }
}
