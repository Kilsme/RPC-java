package tech.insight.kilsme.rpc.register;

import java.util.List;

/**
 * 注册中心抽象：统一 Provider 注册与 Consumer 发现能力。
 */
public interface ServiceRegistry {
    // 初始化注册中心客户端（如 Zookeeper 连接）。
    void init(RegistryConfig registerConfig) throws Exception;

    // Provider 启动后把自身服务信息写入注册中心。
    void  registerService(ServiceMetadata metadata);

    // Consumer 按服务名查询可用 Provider 列表。
    List<ServiceMetadata>fetchServiceList(String service) throws Exception;



}
