package tech.insight.kilsme.rpc.breaker;

/**
 * 熔断器管理器。
 * <p>
 * 按服务维度维护熔断器实例，
 * 让不同服务的熔断状态彼此隔离，避免互相影响。
 */

import tech.insight.kilsme.rpc.consumser.ConsumerProperties;
import tech.insight.kilsme.rpc.register.ServiceMetadata;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class CircuitBreakerManager {
    private final Map<ServiceMetadata ,CircuitBreaker> circuitBreakerMap=new ConcurrentHashMap<>();
    private final ConsumerProperties consumerProperties;

    public CircuitBreakerManager(ConsumerProperties consumerProperties) {
               this.consumerProperties=consumerProperties;
    }

    public CircuitBreaker createOrGetBreaker(ServiceMetadata serviceMetadata){
       return  circuitBreakerMap.computeIfAbsent(serviceMetadata,this::createBreaker);
    }
    private CircuitBreaker createBreaker(ServiceMetadata metadata){
        return new ResponseTimeCircuitBreaker(consumerProperties.getSlowRequestBreakRatio(),consumerProperties.getSlowRequestMs());
    }
}
