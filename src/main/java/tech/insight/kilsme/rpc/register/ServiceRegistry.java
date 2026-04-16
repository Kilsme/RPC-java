package tech.insight.kilsme.rpc.register;

import java.util.List;

/**
 * 注册中心抽象：统一 Provider 注册与 Consumer 发现能力。
 *
 * <p>这个接口把“服务注册”和“服务发现”统一起来，
 * 上层代码不需要关心具体用的是 Zookeeper、Redis，还是其他注册中心实现。</p>
 */
public interface ServiceRegistry {
    /**
     * 初始化注册中心客户端（如创建并启动 Zookeeper 会话）。
     */
    void init(RegistryConfig registerConfig) throws Exception;

    /**
     * Provider 启动后把自身服务信息写入注册中心。
     */
    void  registerService(ServiceMetadata metadata);

    /**
     * Consumer 按服务名查询可用 Provider 列表。
     */
    List<ServiceMetadata>fetchServiceList(String service) throws Exception;



}
