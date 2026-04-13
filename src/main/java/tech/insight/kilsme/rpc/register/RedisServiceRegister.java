package tech.insight.kilsme.rpc.register;

import lombok.extern.slf4j.Slf4j;

import java.util.List;
@Slf4j
public class RedisServiceRegister implements ServiceRegister{
    @Override
    public void init(RegisterConfig registerConfig) throws Exception {
        log.info("redis注册中心未实现");
    }

    @Override
    public void registerService(ServiceMetadata metadata) {
            throw new UnsupportedOperationException("redis注册中心未实现");
    }

    @Override
    public List<ServiceMetadata> fetchServiceList(String service) {
        throw  new UnsupportedOperationException();
    }
}
