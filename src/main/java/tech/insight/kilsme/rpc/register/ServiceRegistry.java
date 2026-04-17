package tech.insight.kilsme.rpc.register;

import java.util.List;

/**
 * 服务注册/发现接口。
 * <p>
 * 抽象 Provider 侧注册与 Consumer 侧发现能力，
 * 便于后续替换不同实现（Zookeeper、Redis、本地内存等）。
 * </p>
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
    List<ServiceMetadata> fetchServiceList(String service) throws Exception;



}
