package tech.insight.kilsme.rpc.provider;

import lombok.Data;
import tech.insight.kilsme.rpc.register.RegistryConfig;

/**
 * Provider 端配置对象。
 *
 * <p>Provider 启动、监听端口、限流和注册中心发布时，都依赖这里的配置。</p>
 */
@Data
public class ProviderProperties {
    // Provider 对外暴露地址：Consumer 会通过这个地址连接到服务端。
    private String host;
    // Provider 对外监听端口。
    private int port;
    // 注册中心配置（用于服务注册）。
    private RegistryConfig registryConfig;
    // Provider 全局并发上限：整个服务端允许同时处理的请求数量。
    private int globalMaxRequest=5;
    // 单 Consumer / 单连接的速率上限。
    private int preConsumerMaxRequest=5;
    // Netty worker 线程数：处理 IO 和业务的工作线程数量。
    private int workThreadNum=4;
}
