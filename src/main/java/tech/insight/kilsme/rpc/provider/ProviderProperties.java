package tech.insight.kilsme.rpc.provider;

import lombok.Data;
import tech.insight.kilsme.rpc.register.RegistryConfig;

/**
 * Provider 端配置对象。
 */
@Data
public class ProviderProperties {
    // Provider 对外暴露地址。
    private String host;
    // Provider 对外监听端口。
    private int port;
    // 注册中心配置（用于服务注册）。
    private RegistryConfig registryConfig;
    // Netty worker 线程数。
    private int workThreadNum=4;
}
