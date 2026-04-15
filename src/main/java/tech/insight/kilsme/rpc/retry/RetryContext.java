package tech.insight.kilsme.rpc.retry;

import lombok.Data;
import tech.insight.kilsme.rpc.loadbalance.LoadBalancer;
import tech.insight.kilsme.rpc.message.Response;
import tech.insight.kilsme.rpc.register.ServiceMetadata;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

@Data
public class RetryContext {
    private ServiceMetadata failService;
    private List<ServiceMetadata>serviceMetadataList;
    private long methodTimeoutMs;
    private long requestTimeoutMs;
    private LoadBalancer loadBalancer;
    //TODO
    private Function<ServiceMetadata, CompletableFuture<Response>> dorpcFunction;
    //进行发送请求函数
    public CompletableFuture<Response>doRpc(ServiceMetadata serviceMetadata){
      return dorpcFunction.apply(serviceMetadata);
    }
}
