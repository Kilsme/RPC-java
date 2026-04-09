package tech.insight.kilsme.rpc;

import tech.insight.kilsme.rpc.consumser.Consumer;

import java.util.concurrent.ExecutionException;

public class Main {
    // Demo 入口：等价于 ConsumerApp，演示消费端如何发起一次 RPC 调用。
    public static void main(String[] args) throws ExecutionException, InterruptedException {
        Consumer consumer = new Consumer();
        System.out.println(consumer.add(1,2));
    }
}
