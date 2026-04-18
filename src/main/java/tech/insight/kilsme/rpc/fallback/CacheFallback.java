package tech.insight.kilsme.rpc.fallback;

import lombok.Data;
import org.checkerframework.checker.units.qual.C;
import tech.insight.kilsme.rpc.exception.RpcException;
import tech.insight.kilsme.rpc.metrices.RpcCallMetrics;

import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class CacheFallback implements Fallback{
    private static final Object NULL_OBJECT=new Object();
    private final Map<InvokeKey,Object> cache=new ConcurrentHashMap<>();
    @Override
    public Object fallback(RpcCallMetrics metrics) {
        InvokeKey invokeKey = new InvokeKey(metrics.getMethod(), metrics.getParams());
       if(cache.get(invokeKey)==NULL_OBJECT){
              return null;
       }
       if(cache.get(invokeKey)==null){
           return null;
       }

       return cache.get(invokeKey);
    }

    @Override
    public void recordMetrics(RpcCallMetrics metrics){
        InvokeKey invokeKey = new InvokeKey(metrics.getMethod(), metrics.getParams());
        Object result = metrics.getResult();
        if(result==null){
           result=NULL_OBJECT;
        }
        cache.put(invokeKey, result);
    }


    @Data
    private class InvokeKey{
           final Method method;
           final Object[]args;

        @Override
        public boolean equals(Object o) {
            if (o == null || getClass() != o.getClass()) return false;
            InvokeKey invokeKey = (InvokeKey) o;
            return Objects.equals(method, invokeKey.method) && Objects.deepEquals(args, invokeKey.args);
        }

        @Override
        public int hashCode() {
            return Objects.hash(method, Arrays.hashCode(args));
        }
    }

}
