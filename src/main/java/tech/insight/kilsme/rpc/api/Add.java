package tech.insight.kilsme.rpc.api;

import tech.insight.kilsme.rpc.fallback.RpcFallback;

/**
 * 示例 RPC 服务契约（接口）。
 * <p>
 * Provider 侧实现该接口并暴露服务，Consumer 侧基于该接口创建动态代理并发起远程调用。
 * 该接口体现了 RPC 的核心思想：双方只依赖“接口定义”，不直接依赖实现类。
 */
@RpcFallback(ConsumerAddImpl.class)
public interface Add {
    /**
     * 计算两个整数之和。
     * Consumer 通过动态代理调用该方法，Provider 通过反射执行实现。
     */
    Integer add(int a,int b);

    /**
     * 计算两个整数之差。
     */
    Integer minus(int a ,int b);

    User mergeAge(User user1,User user2);
}
