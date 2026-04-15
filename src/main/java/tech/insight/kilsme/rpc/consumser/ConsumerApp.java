package tech.insight.kilsme.rpc.consumser;

import tech.insight.kilsme.rpc.api.Add;
import tech.insight.kilsme.rpc.register.RegistryConfig;

import java.util.concurrent.CyclicBarrier;

/**
 * 消费端启动类：演示如何发起一次远程调用。
 */
public class ConsumerApp {
    public static void main(String[] args) throws Exception {
        // 先确保 Provider 已启动，再执行一次 add 的远程调用示例。
        RegistryConfig registerConfig = new RegistryConfig();
        registerConfig.setRegisterType("zookeeper");
        registerConfig.setConnectString("127.0.0.1:2181");
        ConsumerProperties consumerProperties = new ConsumerProperties();
        consumerProperties.setRegistryConfig(registerConfig);
        // 创建代理工厂：内部会初始化注册中心客户端。
        ConsumerProxyFactory proxyFactory = new ConsumerProxyFactory(consumerProperties);
        // 重复调用用于观察连接复用、请求发送和响应回包日志。
        Add addConsumerProxy = proxyFactory.createConsumerProxy(Add.class);
        CyclicBarrier cyclicBarrier = new CyclicBarrier(10);
        for(int i=0;i<10;i++){
            new Thread(() -> {
                try {
                    cyclicBarrier.await();
                    System.out.println(addConsumerProxy.add(1, 2));
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }).start();
        }

    }
}
