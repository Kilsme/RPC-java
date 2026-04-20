package tech.insight.kilsme.rpc.limit;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 基于速率的限流器实现。
 * <p>
 * 用于限制“单位时间内可通过的请求数量”，
 * 常用于保护 Provider 避免突发流量压垮服务。
 *
 * <p>通过控制相邻两次放行请求的最小时间间隔，实现平滑限流。
 * 相比简单令牌桶，这里不做排队阻塞，超阈值时直接返回 false。
 */
public class RateLimiter implements Limiter {
    private final AtomicLong nextTokens;//在什么时间之前不能拿到请求
    private static final int MAX_TRY_ACQUIRE=512;
    private static final long MAX_QUEUE_NS= TimeUnit.MILLISECONDS.toNanos(500);
    private final long  intervalNs;
    /**
     * @param permitsPreSecond 每秒允许通过的请求数
     */
    public RateLimiter(int permitsPreSecond) {
        //间隔多少纳秒进行拿到请求
       intervalNs = TimeUnit.SECONDS.toNanos(1) / permitsPreSecond;
       this.nextTokens=new AtomicLong(0L);

    }
    //采用事件驱动进行平滑限流
    @Override
    public boolean tryAcquire() {
        long now=System.nanoTime();
        //通过cas进行tokens减一
        for(int i=0;i<MAX_TRY_ACQUIRE;i++){
             long pre=nextTokens.get();
             // 如果当前请求要等待超过允许排队窗口，直接拒绝。
             if(now+MAX_QUEUE_NS<pre){
                 return false;
             }
             // 抢占下一个可用时间片；CAS 成功表示本次成功放行。
             if(this.nextTokens.compareAndSet(pre,now+intervalNs)){
                 return true;
             }
        }
        return false;
    }

    @Override
    public void release() {
        //速率限流不需要release
    }

    @Override
    public void release(int permits) {
     //速率限流不需要release
     // 但如果想要支持动态调整速率，可以在这里实现：将 nextTokens 减去 permits * intervalNs，
     // 以提前释放被下一个请求抢占的时间片。
    }
}
