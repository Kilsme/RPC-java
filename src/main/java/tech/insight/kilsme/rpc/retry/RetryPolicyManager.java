package tech.insight.kilsme.rpc.retry;

import lombok.extern.slf4j.Slf4j;
import tech.insight.kilsme.rpc.spi.Spi;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;

@Slf4j
public class RetryPolicyManager {
    public RetryPolicyManager() {
        init();
    }
     private final Map<String ,RetryPolicy> nameMap=new HashMap<>();
     public RetryPolicy getRetryPolicy(String name){
         return nameMap.get(name);
     }
    private void init() {
        // 通过SPI加载所有的RetryPolicy实现类，并将它们注册到一个Map中，方便后续根据名称或其他标识来获取对应的RetryPolicy实例。
        for (RetryPolicy retryPolicy : ServiceLoader.load(RetryPolicy.class)) {
            Class<? extends RetryPolicy> aClass = retryPolicy.getClass();
            if (!aClass.isAnnotationPresent(Spi.class)) {
                log.warn("RetryPolicy实现类{}缺少@Spi注解，已忽略", aClass.getName());
                continue;
            }
            Spi spiAnnotation = aClass.getAnnotation(Spi.class);
            nameMap.put(spiAnnotation.value(), retryPolicy);
        }
    }
}
