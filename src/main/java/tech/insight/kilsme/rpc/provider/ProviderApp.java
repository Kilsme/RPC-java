package tech.insight.kilsme.rpc.provider;

import tech.insight.kilsme.rpc.api.Add;
import tech.insight.kilsme.rpc.register.RegistryConfig;

/**
 * 服务端启动类：注册服务并监听端口。
 *
 * <p>这是 Provider 进程的启动入口，负责把本地实现对象暴露出去，并发布到注册中心。</p>
 */
public class ProviderApp {
    public static void main(String[] args) {
        // 1) 准备注册中心配置，Provider 启动后会把服务地址注册进去。
        RegistryConfig registerConfig=new RegistryConfig();
        registerConfig.setRegisterType("zookeeper");
        registerConfig.setConnectString("127.0.0.1:2181");
        ProviderProperties providerProperties = new ProviderProperties();
        providerProperties.setHost("127.0.0.1");
        providerProperties.setPort(8889);
        providerProperties.setRegistryConfig(registerConfig);
        // 2) 创建 ProviderServer，设置监听地址与端口。
        ProviderServer providerServer = new ProviderServer(providerProperties);
        // 3) 注册服务实现（接口 -> 实例）。
        // 这里把接口和实现先放到本地注册表，后面启动时再统一写入注册中心。
        providerServer.register(Add.class,new AddImpl());
        // 4) 启动 Netty 服务并向注册中心发布服务元数据。
        providerServer.start();

    }
}
