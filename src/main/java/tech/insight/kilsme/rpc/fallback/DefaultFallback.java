package tech.insight.kilsme.rpc.fallback;

import lombok.extern.slf4j.Slf4j;
import tech.insight.kilsme.rpc.metrices.RpcCallMetrics;

import java.lang.reflect.InvocationTargetException;

@Slf4j//采用组合模式
public class DefaultFallback implements  Fallback {
    private final CacheFallback cacheFallback;
    private final MockFallback mockFallback;

    public DefaultFallback(CacheFallback cacheFallback, MockFallback mockFallback){
        this.cacheFallback=cacheFallback;
        this.mockFallback=mockFallback;
    }

    @Override
    public void recordMetrics(RpcCallMetrics metrics) {
      this.cacheFallback.recordMetrics(metrics);
      this.mockFallback.recordMetrics(metrics);

    }

    @Override
    public Object fallback(RpcCallMetrics metrics) throws InvocationTargetException, IllegalAccessException {
       try{
           Object fallback = cacheFallback.fallback(metrics);
           if(fallback!=null){
               return fallback;
           }
           return mockFallback.fallback(metrics);

       } catch (Exception e) {
           log.warn("缓存没有生效");
           return mockFallback.fallback(metrics);
       }
    }
}
