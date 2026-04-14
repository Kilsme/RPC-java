package tech.insight.kilsme.rpc.consumser;

import lombok.Data;
import tech.insight.kilsme.rpc.register.RegistryConfig;

//定义消费者配置
@Data
public class ConsumerProperties {
    private Integer workThreadNum=4;
    private Integer connectTimeoutMs=3000;
    private Integer requestTimeoutMs=3000;
    private String  loadBalancePolicy="robin";
    private RegistryConfig registryConfig=new RegistryConfig();

}
