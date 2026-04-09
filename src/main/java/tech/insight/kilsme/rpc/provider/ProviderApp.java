package tech.insight.kilsme.rpc.provider;

public class ProviderApp {
    public static void main(String[] args) {
        // 启动 Provider，监听 8888 端口，等待 Consumer 发起 RPC 调用。
        ProviderServer providerServer = new ProviderServer(8888);
        providerServer.start();

    }
}
