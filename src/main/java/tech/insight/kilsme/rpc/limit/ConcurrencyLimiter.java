package tech.insight.kilsme.rpc.limit;

import java.util.concurrent.Semaphore;

/**
 * 并发数限流实现。
 * <p>
 * 通过计数当前在处理中的请求数量来限制并发，
 * 适合保护线程池、数据库连接池等“并发敏感资源”。
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
