package tech.insight.kilsme.rpc.consumser;

import tech.insight.kilsme.rpc.api.Add;
import tech.insight.kilsme.rpc.register.RegisterConfig;
import tech.insight.kilsme.rpc.register.ServiceMetadata;
import tech.insight.kilsme.rpc.register.ServiceRegister;

import java.util.List;
import java.util.concurrent.ExecutionException;

/**
 * 消费端启动类：演示如何发起一次远程调用。
 */
public class ConsumerApp {
    public static void main(String[] args) throws Exception {
        // 先确保 Provider 已启动，再执行一次 add 的远程调用示例。
        RegisterConfig registerConfig = new RegisterConfig();
        registerConfig.setRegisterType("zookeeper");
        registerConfig.setConnectString("127.0.0.1:2181");
        ConsumerProxyFactory proxyFactory = new ConsumerProxyFactory(registerConfig);
        // 重复调用用于观察连接复用、请求发送和响应回包日志。
        Add addConsumerProxy = proxyFactory.createConsumerProxy(Add.class);
        while (true) {
            try {
                System.out.println(addConsumerProxy.add(1, 2));
            } catch (Exception e) {
                e.printStackTrace();
            }
            Thread.sleep(1000);
        }

    }
}
