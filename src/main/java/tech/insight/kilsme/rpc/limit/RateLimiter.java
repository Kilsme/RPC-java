package tech.insight.kilsme.rpc.limit;

import io.netty.channel.DefaultEventLoop;
import io.netty.channel.EventLoop;
import org.checkerframework.checker.units.qual.A;

import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public class RateLimiter implements Limiter {
    private final AtomicLong nextTokens;//在什么时间之前不能拿到请求
    private static final int MAX_TRY_ACQUIRE=512;
    private static final long MAX_QUEUE_NS=TimeUnit.MILLISECONDS.toNanos(500);
    private final long  intervalNs;
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
             if(now+MAX_QUEUE_NS<pre){
                 return false;
             }
             if(this.nextTokens.compareAndSet(pre,now+intervalNs)){
                 return true;
             }
        }
        return false;
    }

    @Override
    public void release() {

    }
}
