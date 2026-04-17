package tech.insight.kilsme.rpc.register;

import lombok.Data;

/**
 * 服务实例元数据。
 * <p>
 * 描述一个可调用的 Provider 实例（服务名、地址、端口、分组等），
 * 是注册中心存储与消费者路由选择的基础数据结构。
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
