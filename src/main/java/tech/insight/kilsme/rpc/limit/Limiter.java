package tech.insight.kilsme.rpc.limit;

/**
 * 限流器统一抽象。
 * <p>
 * Provider 或 Consumer 可以按需要实现不同流控算法（令牌桶、并发计数等），
 * 统一通过 acquire/release 语义接入业务处理链路。
 *
 * <p>Consumer 侧用于保护自身资源与下游 Provider；
 * Provider 侧也可复用相同抽象做入口流量保护。
 */
public interface Limiter {
    /**
     * 尝试申请一个许可。
     *
     * @return true 表示放行；false 表示触发限流
     */
    boolean tryAcquire();

    /**
     * 归还许可。
     *
     * <p>对于并发型限流器需要释放；
     * 对于纯速率型限流器可为空实现。
     */
    default void release() {
        release(1);
    }
    //provider
    //并发限流    服务器的承载上线=>全局限流
    //速率限流 100/1s 放置突然的流量剧增=>局部限流
    //consumer

    void release(int permits);
}
