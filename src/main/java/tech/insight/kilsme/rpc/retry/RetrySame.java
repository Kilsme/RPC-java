package tech.insight.kilsme.rpc.retry;

import lombok.extern.slf4j.Slf4j;
import tech.insight.kilsme.rpc.exception.RpcException;
import tech.insight.kilsme.rpc.message.Response;

import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * 同节点重试策略：在同一个失败节点上进行有限次数重试。
 * <p>
 * 使用指数退避 + 随机抖动，降低瞬时高并发下的重试风暴风险。
 */
@Slf4j
public class RetrySame implements RetryPolicy {
    // 最大重试次数。
    final int retryMax = 3;
    private final Random random=new Random();
    // 重试开始时间，用于与方法总超时进行对比。
    long startTime = System.currentTimeMillis();

    @Override
    public Response retry(RetryContext retryContext) throws Exception {
        int retryCount = 0;
        while (retryCount < retryMax) {
            // 计算下一次重试前等待时间（指数退避）。
            long nextDelay=nextDelay(retryCount);
            if(nextDelay>1000){
                // 限制单次退避上限，避免等待过久。
                nextDelay=1000;
            }

            // 计算方法剩余时间，避免重试超出方法级超时预算。
            long methodTimeoutMs = retryContext.getMethodTimeoutMs() - (System.currentTimeMillis() - startTime);
            if (methodTimeoutMs <= 0||nextDelay>=methodTimeoutMs) {
                throw new TimeoutException();
            }
            // 阻塞当前调用线程等待退避时间。
            Thread.sleep(nextDelay);
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

    // 指数退避：100ms * 2^retryCount，再加 0~49ms 随机扰动。
    private long nextDelay(int retryCount){
      return  100L*(1L<<retryCount)+ random.nextInt(50);//使用位运算加快运算时间，加上 0~49 的扰动
    }

}
