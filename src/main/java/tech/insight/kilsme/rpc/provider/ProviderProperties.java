package tech.insight.kilsme.rpc.provider;

import lombok.Data;
import tech.insight.kilsme.rpc.register.RegistryConfig;
@Data
public class ProviderProperties {
    private String host;
    private int port;
    private RegistryConfig registryConfig;
    private int workThreadNum=4;
}
