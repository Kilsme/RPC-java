package tech.insight.kilsme.rpc;

import java.util.concurrent.ExecutionException;

/**
 * 简单入口：直接调用消费端发起一次 RPC 请求。
 */
public class Main {
    // 等价于 ConsumerApp，便于快速本地验证一次端到端调用。
    // 当前 main 为空实现，建议直接运行 consumser/ConsumerApp 与 provider/ProviderApp。
    public static void main(String[] args) throws ExecutionException, InterruptedException {
    }
}
