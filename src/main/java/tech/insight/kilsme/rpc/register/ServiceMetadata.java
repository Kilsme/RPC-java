package tech.insight.kilsme.rpc.register;

import lombok.Data;

/**
 * 服务实例元数据：注册中心中一条服务节点记录。
 */
@Data
public class ServiceMetadata {
   // Provider IP 或域名。
   private String host;
   // Provider 对外监听端口。
   private int port;
   // 服务名，通常是接口全限定名。
   private String serviceName;
}
