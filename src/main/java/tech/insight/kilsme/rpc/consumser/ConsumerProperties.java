package tech.insight.kilsme.rpc.consumser;

import lombok.Data;
import tech.insight.kilsme.rpc.register.RegistryConfig;

/**
 * 消费端配置对象。
 *
 * <p>这里集中保存 Consumer 运行时的核心参数：线程、超时、负载均衡、重试、限流和注册中心配置。
 * 这样 ConsumerProxyFactory / ConnectionManager / InFlightRequestManager 可以统一读取这些设置。</p>
 */
@Data
public class ConsumerProperties {
    // Netty worker 线程数：影响 Consumer 侧网络处理能力。
    private Integer workThreadNum=4;
    // 建连超时时间（毫秒）：与 Provider 建立 TCP 连接时最多等待多久。
    private Integer connectTimeoutMs=3000;
    // 单次请求超时时间（毫秒）：一次 RPC 请求从发出到收到响应的等待上限。
    private Integer requestTimeoutMs=3000;
    // 方法总超时时间（毫秒），包含重试耗时。
    // 这个值比 requestTimeoutMs 更大，因为它允许“失败后再补救几次”。
    private Integer methodTimeOutMs=10000;
    // 负载均衡策略：robin/random。
    private String  loadBalancePolicy="robin";
    // 重试策略：retrySame/failover/forking。
    private String  retryPolicy="forking";
    // 注册中心配置（类型、地址等）。
    private RegistryConfig registryConfig=new RegistryConfig();
    // Consumer 全局并发限流：最多允许多少个 RPC 在“在途”状态。
    private int rpcPreSecond=5;
    // 单 Provider / 单连接限流：限制对同一个 Provider 的请求压力。
    private int rpcPreChannel=2;

}
