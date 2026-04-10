package tech.insight.kilsme.rpc;

import tech.insight.kilsme.rpc.consumser.Consumer;

import java.util.concurrent.ExecutionException;

/**
 * 简单入口：直接调用消费端发起一次 RPC 请求。
 */
public class Main {
    // 等价于 ConsumerApp，便于快速本地验证。
    public static void main(String[] args) throws ExecutionException, InterruptedException {
        Consumer consumer = new Consumer();
        System.out.println(consumer.add(1,2));
    }
}
