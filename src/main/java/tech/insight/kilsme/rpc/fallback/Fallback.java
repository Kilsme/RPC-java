package tech.insight.kilsme.rpc.fallback;

import tech.insight.kilsme.rpc.metrices.RpcCallMetrics;

import java.lang.reflect.InvocationTargetException;

public interface Fallback {
    Object fallback(RpcCallMetrics metrics) throws InvocationTargetException, IllegalAccessException;
    default  void recordMetrics(RpcCallMetrics metrics){

    }

}
