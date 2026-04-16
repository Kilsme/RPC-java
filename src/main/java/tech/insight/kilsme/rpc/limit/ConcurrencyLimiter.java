package tech.insight.kilsme.rpc.limit;

import java.util.concurrent.Semaphore;

/**
 * 并发限流器（基于 Semaphore）。
 *
 * <p>限制“同一时刻同时进行中的请求数”，适合控制系统资源上限。
 */
public class ConcurrencyLimiter implements Limiter{
     private final Semaphore semaphore;

    /**
     * @param limitNum 允许并发执行的最大请求数
     */
    public ConcurrencyLimiter(int limitNum) {
        this.semaphore = new Semaphore(limitNum);
    }

    @Override
    public boolean tryAcquire() {
       return semaphore.tryAcquire();
    }

    @Override
    public void release(int permits) {
          semaphore.release(permits);
    }
}
