package tech.insight.kilsme.rpc.api;

/**
 * RPC 服务契约：定义一个加法方法。
 */
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
}
