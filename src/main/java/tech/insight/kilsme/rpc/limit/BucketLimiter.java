package tech.insight.kilsme.rpc.limit;

import io.netty.channel.DefaultEventLoop;
import io.netty.channel.EventLoop;

import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
@Deprecated
/**
 * 令牌桶限流器（已弃用）。
 *
 * <p>该实现按秒重置令牌，流量整形不够平滑，当前由 {@link RateLimiter} 替代。
 */
public class BucketLimiter implements Limiter {
    private final AtomicInteger tokens;//使用令牌桶的算法实现
    //设置成守护线程
    private static final EventLoop REFILL_EVENT_LOOP = new DefaultEventLoop(new ThreadFactory() {
        @Override
        public Thread newThread(Runnable r) {
            Thread t = new Thread(r,"refill_event_loop");
            t.setDaemon(true);
            return t;
        }
    });//定时补充令牌的事件循环
    private final ScheduledFuture<?> refillSchedule;
    public BucketLimiter(int permitsPreSecond) {
        this.tokens = new AtomicInteger(permitsPreSecond);
      refillSchedule=REFILL_EVENT_LOOP.scheduleAtFixedRate(() -> tokens.set(permitsPreSecond), 1, 1, TimeUnit.SECONDS);
    }

    /**
     * 关闭定时补充任务，避免应用退出时线程泄漏。
     */
    public void destroy(){
        refillSchedule.cancel(false);
    }
    //1000 1000 1000
    //0 -->0.9-->1

    @Override
    public boolean tryAcquire() {
        //通过cas进行tokens减一
       while(true){
           int currentTokens= tokens.get();
           if(currentTokens<=0){
               return false;
           }
           if(tokens.compareAndSet(currentTokens,currentTokens-1)){
               return true;
           }
       }
    }

    @Override
    public void release() {

    }

    @Override
    public void release(int permits) {

    }
}
