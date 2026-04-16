package tech.insight.kilsme.rpc.register;

import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 注册中心门面：对外提供统一接口，内部按配置委托到具体实现。
 *
 * <p>调用方只依赖这个类，不需要知道底层是 Zookeeper 还是 Redis。
 * 它还带有一个简单的本地缓存，用于注册中心临时不可用时的降级查询。</p>
 */
@Slf4j
public class DefaultServiceRegister implements ServiceRegistry {
    // 本地兜底缓存：当注册中心短暂不可用时返回最近一次查询结果。
    // 这属于“可用性优先”的设计，避免注册中心抖动直接影响调用。
    private final Map<String, List<ServiceMetadata>> cache = new ConcurrentHashMap<>();
    // 真正执行注册/发现的实现（Zookeeper 或 Redis 等）。
    private ServiceRegistry delegate;
    @Override
    public void init(RegistryConfig registerConfig) throws Exception {
        // 根据配置创建具体实现，再执行真实初始化。
        // 这里相当于“工厂 + 门面”的组合。
        this.delegate = createServiceRegister(registerConfig);
        this.delegate.init(registerConfig);
    }

    @Override
    public void registerService(ServiceMetadata metadata) {
        // 统一走 delegate，不让上层关心具体注册中心实现。
        log.info("向{}注册了一个service{}", delegate.getClass(), metadata.getServiceName());
        delegate.registerService(metadata);
    }

    @Override
    public List<ServiceMetadata> fetchServiceList(String service) {
        try {
            // 正常路径：优先读注册中心，并刷新本地缓存。
            // 这样后续如果注册中心短暂不可用，还能用最近一次的查询结果兜底。
            List<ServiceMetadata> serviceMetadata = delegate.fetchServiceList(service);
            cache.put(service, serviceMetadata);
            return serviceMetadata;
        } catch (Exception e) {
            // 异常路径：降级使用本地缓存，避免调用方直接失败。
            log.error("{}注册中心查询出现异常，service={}", delegate.getClass().getSimpleName(), service, e);
            return cache.getOrDefault(service, new ArrayList<>());

        }
    }

    private ServiceRegistry createServiceRegister(RegistryConfig config) {
        // 按 registerType 选择具体注册中心实现。
        if (config.getRegisterType().equals("zookeeper")) {
            return new ZookeeperServiceRegister();
        }
        if (config.getRegisterType().equals("redis")) {
            return new RedisServiceRegister();
        }
        throw new IllegalArgumentException(config.getRegisterType() + "没有实现");
    }
}
