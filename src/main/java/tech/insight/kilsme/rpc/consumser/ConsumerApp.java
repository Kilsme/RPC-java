package tech.insight.kilsme.rpc.consumser;

import tech.insight.kilsme.rpc.api.Add;
import tech.insight.kilsme.rpc.api.User;
import tech.insight.kilsme.rpc.register.RegistryConfig;

import java.util.HashMap;
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
        GenericConsumer genericConsumer=proxyFactory.createConsumerProxy(GenericConsumer.class);
        System.out.println(genericConsumer.$invoke(Add.class.getName(), "add",
                new String[]{"int", "int"}, new Object[]{1, 2}));

        //aaa rpc+auth ===>gateway-->provider 进行泛化调用
        HashMap<String, Object> user1 = new HashMap<>();
        user1.put("age",1);
        user1.put("name","consumer创建");
        HashMap<String, Object> user2 = new HashMap<>();
        user2.put("age",2);
        user2.put("name","consumer创建");
        System.out.println(genericConsumer.$invoke(Add.class.getName(), "mergeAge",
                new String[]{User.class.getName(), User.class.getName()},
                new Object[]{user1, user2}));

    }
}
