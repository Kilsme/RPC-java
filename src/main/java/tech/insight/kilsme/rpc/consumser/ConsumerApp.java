package tech.insight.kilsme.rpc.consumser;

import tech.insight.kilsme.rpc.api.Add;
import tech.insight.kilsme.rpc.register.RegistryConfig;

import java.util.concurrent.CyclicBarrier;

/**
 * Consumer 启动入口。
 * <p>
 * 通过 ConsumerProxyFactory 创建接口代理，
 * 再像本地方法一样调用远程服务，演示 RPC 调用链路。
 * </p>
 *
 * <p>这个类的重点不是业务逻辑，而是演示 Consumer 如何通过动态代理调用远程接口。</p>
 */
public class ConsumerApp {
    public static void main(String[] args) throws Exception {
        // 先确保 Provider 已启动，再执行一次 add 的远程调用示例。
        RegistryConfig registerConfig = new RegistryConfig();
        registerConfig.setRegisterType("zookeeper");
        registerConfig.setConnectString("127.0.0.1:2181");
        ConsumerProperties consumerProperties = new ConsumerProperties();
        consumerProperties.setRpcPreSecond(1);
        consumerProperties.setRpcPreChannel(1);
        consumerProperties.setRegistryConfig(registerConfig);
        // 创建代理工厂：内部会初始化注册中心客户端。
        ConsumerProxyFactory proxyFactory = new ConsumerProxyFactory(consumerProperties);
        // 重复调用用于观察连接复用、请求发送和响应回包日志。
        // 这里用 10 个线程同时发起调用，是为了更容易观察限流、并发和回包匹配过程。
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
