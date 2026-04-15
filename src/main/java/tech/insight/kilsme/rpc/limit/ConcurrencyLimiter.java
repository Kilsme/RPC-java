package tech.insight.kilsme.rpc.limit;

import java.util.concurrent.Semaphore;
//并发限流器
public class ConcurrencyLimiter implements Limiter{
     private final Semaphore semaphore;

    public ConcurrencyLimiter(int limitNum) {
        this.semaphore = new Semaphore(limitNum);
    }

    @Override
    public boolean tryAcquire() {
       return semaphore.tryAcquire();
    }

    @Override
    public void release() {
        semaphore.release();
    }
}
