package tech.insight.kilsme.rpc.retry;

import lombok.extern.slf4j.Slf4j;
import tech.insight.kilsme.rpc.exception.RpcException;
import tech.insight.kilsme.rpc.message.Response;

import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Slf4j
public class RetrySame implements RetryPolicy {
    final int retryMax = 3;
    private final Random random=new Random();
    //重试需要进行重试时间的记录
    //防止遭遇网络风暴 采用指数回避
    long startTime = System.currentTimeMillis();

    @Override
    public Response retry(RetryContext retryContext) throws Exception {
        int retryCount = 0;
        while (retryCount < retryMax) {
            long nextDelay=nextDelay(retryCount);
            if(nextDelay>1000){
                nextDelay=1000;
            }

            long methodTimeoutMs = retryContext.getMethodTimeoutMs() - (System.currentTimeMillis() - startTime);
            if (methodTimeoutMs <= 0||nextDelay>=methodTimeoutMs) {
                throw new TimeoutException();
            }
            Thread.sleep(nextDelay);//阻塞的是consumer的线程(主线程)，不影响其他请求的处理
            try {
                CompletableFuture<Response> future = retryContext.doRpc(retryContext.getFailService());
                return future.get(retryContext.getMethodTimeoutMs(), TimeUnit.MILLISECONDS);
            } catch (Exception e) {
                log.error("重试失败");
            }
            retryCount++;
        }
        throw new RpcException("重试失败");
    }
    private long nextDelay(int retryCount){
      return  100L*(1L<<retryCount)+ random.nextInt(50);//使用位运算加快运算时间，加上 0~49 的扰动
    }

}
