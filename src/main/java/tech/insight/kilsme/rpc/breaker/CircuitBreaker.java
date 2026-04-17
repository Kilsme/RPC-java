package tech.insight.kilsme.rpc.breaker;

/**
 * 熔断器策略接口。
 * <p>
 * 用于在下游异常率/慢调用率过高时快速失败，
 * 防止故障持续扩散并保护系统资源。
 */

import tech.insight.kilsme.rpc.metrices.RpcCallMetrics;

public interface CircuitBreaker {
    boolean allowRequest();//判断是否返回

    void recordRpc(RpcCallMetrics metrics);//记录调用结果
   //状态
    enum State{
        CLOSED,OPEN,HALF_OPEN
    }
}
