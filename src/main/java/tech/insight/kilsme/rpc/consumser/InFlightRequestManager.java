package tech.insight.kilsme.rpc.consumser;

import io.netty.util.HashedWheelTimer;
import io.netty.util.Timeout;
import lombok.extern.slf4j.Slf4j;
import tech.insight.kilsme.rpc.exception.LimitException;
import tech.insight.kilsme.rpc.limit.ConcurrencyLimiter;
import tech.insight.kilsme.rpc.limit.Limiter;
import tech.insight.kilsme.rpc.limit.RateLimiter;
import tech.insight.kilsme.rpc.message.Request;
import tech.insight.kilsme.rpc.message.Response;
import tech.insight.kilsme.rpc.register.ServiceMetadata;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
@Slf4j
public class InFlightRequestManager {
    private  final Map<ServiceMetadata,Limiter>channelLimiterMap;//每一个hashmap都有自己的limiter进行限流
    private final Map<Integer, CompletableFuture<Response>> inFlightRequestTable;
    private final HashedWheelTimer timeoutTimer;//定义时间轮
    //创建限流器
    private final Limiter globalLimiter;
    private final ConsumerProperties consumerProperties;
    public InFlightRequestManager(ConsumerProperties consumerProperties) {
        this.inFlightRequestTable = new ConcurrentHashMap<>();
        this.consumerProperties=consumerProperties;
        this.timeoutTimer = new HashedWheelTimer(100,TimeUnit.MILLISECONDS,256);
        this.globalLimiter =new ConcurrencyLimiter(consumerProperties.getRpcPreSecond());
        this.channelLimiterMap=new ConcurrentHashMap<>();
    }

    public CompletableFuture<Response> inFlightRequestTable(Request request, long timeOuts, ServiceMetadata serviceMetadata){
        CompletableFuture<Response> responseFuture = new CompletableFuture<>();
         if(!globalLimiter.tryAcquire()){
             responseFuture.completeExceptionally(new LimitException("当前请求被全局限流"));
             return responseFuture;
         }
        Limiter channelLimiter = channelLimiterMap.computeIfAbsent(serviceMetadata,
                k -> new RateLimiter(consumerProperties.getRpcPreChannel()));
         if(!channelLimiter.tryAcquire()){
             responseFuture.completeExceptionally(new LimitException("channel限流，当前在途请求超过阈值"));
             return responseFuture;
         }
        inFlightRequestTable.put(request.getRequestId(), responseFuture);//防止通信速度过快，导致response回来找不到request在table中
        //进行兜底策略 定时任务
        Timeout timeout = timeoutTimer.newTimeout((t) -> responseFuture.completeExceptionally(new TimeoutException()),
                timeOuts,
                TimeUnit.MILLISECONDS);
        responseFuture.whenComplete((f, e) -> {
            inFlightRequestTable.remove(request.getRequestId());
            timeout.cancel();
            globalLimiter.release();
            channelLimiter.release();
        });//无论是否成功，结束后都要在在途请求中进行移除
        return responseFuture;
    }
    public boolean completeRuest(int requestId,Response response){
        CompletableFuture<Response>future=inFlightRequestTable.remove(requestId);
        if(future==null){
            log.warn("未找到对应的请求，requestId={}",requestId);
            return false;
        }

        return  future.complete(response);
    }
    public boolean completeExceptionallyRequest(int requestId,Exception e){
        CompletableFuture<Response>future=inFlightRequestTable.remove(requestId);
        if(future==null){
            log.warn("未找到对应的请求，requestId={},空闲异常",requestId,e);
            return false;
        }
        return  future.completeExceptionally(e);
    }

}
