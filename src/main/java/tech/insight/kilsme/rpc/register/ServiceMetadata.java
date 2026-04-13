package tech.insight.kilsme.rpc.register;

import lombok.Data;

@Data
public class ServiceMetadata {
   private String host;
   private int port;
   private String serviceName;
}
