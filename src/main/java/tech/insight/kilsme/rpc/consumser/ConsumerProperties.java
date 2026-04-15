package tech.insight.kilsme.rpc.consumser;

import lombok.Data;
import tech.insight.kilsme.rpc.register.RegistryConfig;

/**
 * 消费端配置对象。
 * <p>
 * 用于控制 Consumer 的线程数、超时、负载均衡和重试策略等运行参数。
 */
@Data
public class ConsumerProperties {
    // Netty worker 线程数。
    private Integer workThreadNum=4;
    // 建连超时时间（毫秒）。
    private Integer connectTimeoutMs=3000;
    // 单次请求超时时间（毫秒）。
    private Integer requestTimeoutMs=3000;
    // 方法总超时时间（毫秒），包含重试耗时。
    private Integer methodTimeOutMs=10000;
    // 负载均衡策略：robin/random。
    private String  loadBalancePolicy="robin";
    // 重试策略：retrySame/failover/forking。
    private String  retryPolicy="forking";
    // 注册中心配置（类型、地址等）。
    private RegistryConfig registryConfig=new RegistryConfig();
    private int rpcPreSecond=10;//每秒只能进行调用十次rpc
    private int rpcPreChannel=5;

}
