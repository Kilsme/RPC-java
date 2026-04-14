package tech.insight.kilsme.rpc.register;

import lombok.Data;

/**
 * 注册中心配置项。
 */
@Data
public class RegistryConfig {
    // 注册中心类型：当前支持 zookeeper，redis 预留。
    private  String registerType="zookeeper";
    // 注册中心连接串，例如 127.0.0.1:2181。
    private String connectString;
}
