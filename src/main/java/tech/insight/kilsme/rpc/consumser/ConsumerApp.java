package tech.insight.kilsme.rpc.consumser;

import java.util.concurrent.ExecutionException;

public class ConsumerApp {
    public static void main(String[] args) throws ExecutionException, InterruptedException {
            // 先确保 Provider 已启动，再执行一次 add 的远程调用示例。
            Consumer consumer = new Consumer();
            System.out.println(consumer.add(1,2));
    }
}
