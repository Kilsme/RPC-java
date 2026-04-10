package tech.insight.kilsme.rpc.provider;

import tech.insight.kilsme.rpc.api.Add;

/**
 * 服务端启动类：注册服务并监听端口。
 */
public class ProviderApp {
    public static void main(String[] args) {
        // 启动 Provider，监听 8888 端口，等待 Consumer 发起 RPC 调用。
        ProviderServer providerServer = new ProviderServer(8888);
        providerServer.register(Add.class,new AddImpl());
        providerServer.start();

    }
}
