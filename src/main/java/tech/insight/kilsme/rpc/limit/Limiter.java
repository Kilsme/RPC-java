package tech.insight.kilsme.rpc.limit;

public interface Limiter {
    //申请拿到凭证 拿不到直接进行返回
    boolean tryAcquire();

    void release();
    //provider
    //并发限流    服务器的承载上线=>全局限流
    //速率限流 100/1s 放置突然的流量剧增=>局部限流
    //consumer
}
