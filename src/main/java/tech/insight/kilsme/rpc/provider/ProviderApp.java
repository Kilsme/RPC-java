package tech.insight.kilsme.rpc.provider;

import tech.insight.kilsme.rpc.api.Add;
import tech.insight.kilsme.rpc.register.RegisterConfig;

/**
 * 服务端启动类：注册服务并监听端口。
 */
public class ProviderApp {
    public static void main(String[] args) {
        RegisterConfig registerConfig=new RegisterConfig();
        registerConfig.setRegisterType("zookeeper");
        registerConfig.setConnectString("127.0.0.1:2181");
        // 启动 Provider，监听 8888 端口，等待 Consumer 发起 RPC 调用。
        ProviderServer providerServer = new ProviderServer(8887,"127.0.0.1",registerConfig);
        // 先注册接口实现，再启动网络监听。
        providerServer.register(Add.class,new AddImpl());
        providerServer.start();

    }
}
