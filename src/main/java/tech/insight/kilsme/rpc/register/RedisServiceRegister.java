package tech.insight.kilsme.rpc.register;

import lombok.extern.slf4j.Slf4j;

import java.util.List;

/**
 * Redis 注册中心占位实现：当前仅保留扩展点，未落地功能。
 */
@Slf4j
public class RedisServiceRegister implements ServiceRegistry {
    @Override
    public void init(RegistryConfig registerConfig) throws Exception {
        // 仅记录提示，避免误以为已具备 Redis 注册能力。
        log.info("redis注册中心未实现");
    }

    @Override
    public void registerService(ServiceMetadata metadata) {
            // 明确抛异常，提示调用方该实现不可用。
            throw new UnsupportedOperationException("redis注册中心未实现");
    }

    @Override
    public List<ServiceMetadata> fetchServiceList(String service) {
        // 同样未实现服务发现能力。
        throw  new UnsupportedOperationException();
    }
}
