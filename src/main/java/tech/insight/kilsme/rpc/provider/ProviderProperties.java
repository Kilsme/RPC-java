package tech.insight.kilsme.rpc.provider;

import lombok.Data;
import tech.insight.kilsme.rpc.register.RegistryConfig;

/**
 * Provider 端运行配置。
 * <p>
 * 包含监听端口、限流开关、注册中心配置等，
 * 由 ProviderApp 在启动时加载并传入 ProviderServer。
 * </p>
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

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public RegistryConfig getRegistryConfig() {
        return registryConfig;
    }

    public void setRegistryConfig(RegistryConfig registryConfig) {
        this.registryConfig = registryConfig;
    }

    public int getGlobalMaxRequest() {
        return globalMaxRequest;
    }

    public void setGlobalMaxRequest(int globalMaxRequest) {
        this.globalMaxRequest = globalMaxRequest;
    }

    public int getPreConsumerMaxRequest() {
        return preConsumerMaxRequest;
    }

    public void setPreConsumerMaxRequest(int preConsumerMaxRequest) {
        this.preConsumerMaxRequest = preConsumerMaxRequest;
    }

    public int getWorkThreadNum() {
        return workThreadNum;
    }

    public void setWorkThreadNum(int workThreadNum) {
        this.workThreadNum = workThreadNum;
    }
}
