package tech.insight.kilsme.rpc.breaker;

import tech.insight.kilsme.rpc.consumser.ConsumerProperties;
import tech.insight.kilsme.rpc.register.ServiceMetadata;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class CircuitBreakerManager {
    private final Map<ServiceMetadata ,CircuitBreaker> circuitBreakerMap=new ConcurrentHashMap<>();

    public CircuitBreakerManager(ConsumerProperties consumerProperties) {

    }

    public CircuitBreaker createOrGetBreaker(ServiceMetadata serviceMetadata){
       return  circuitBreakerMap.computeIfAbsent(serviceMetadata,this::createBreaker);
    }
    private CircuitBreaker createBreaker(ServiceMetadata metadata){
        return null;
    }
}
