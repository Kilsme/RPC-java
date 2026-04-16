package tech.insight.kilsme.rpc.breaker;

import tech.insight.kilsme.rpc.metrices.RpcCallMetrics;

public interface CircuitBreaker {
    boolean allowRequest();//判断是否返回
    void recordRpc(RpcCallMetrics metrics);//记录调用结果
}
