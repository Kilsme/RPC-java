package tech.insight.kilsme.rpc.register;

import com.google.common.util.concurrent.Service;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
public class DefaultServiceRegister implements ServiceRegister {
    private final Map<String, List<ServiceMetadata>> cache = new ConcurrentHashMap<>();
    private ServiceRegister delegate;

    @Override
    public void init(RegisterConfig registerConfig) throws Exception {
        this.delegate = createServiceRegister(registerConfig);
        this.delegate.init(registerConfig);
    }

    @Override
    public void registerService(ServiceMetadata metadata) {
        log.info("向{}注册了一个service{}", delegate.getClass(), metadata.getServiceName());
        delegate.registerService(metadata);
    }

    @Override
    public List<ServiceMetadata> fetchServiceList(String service) {
        try {
            List<ServiceMetadata> serviceMetadata = delegate.fetchServiceList(service);
            cache.put(service, serviceMetadata);
            return serviceMetadata;
        } catch (Exception e) {
            log.error("{}注册中心查询出现异常，service={}", delegate.getClass().getSimpleName(), service, e);
            return cache.getOrDefault(service, new ArrayList<>());

        }
    }

    private ServiceRegister createServiceRegister(RegisterConfig config) {
        if (config.getRegisterType().equals("zookeeper")) {
            return new ZookeeperServiceRegister();
        }
        if (config.getRegisterType().equals("redis")) {
            return new RedisServiceRegister();
        }
        throw new IllegalArgumentException(config.getRegisterType() + "没有实现");

    }
}
