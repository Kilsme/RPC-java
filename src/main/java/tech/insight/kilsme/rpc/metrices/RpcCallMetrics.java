package tech.insight.kilsme.rpc.metrices;

import lombok.Data;
import tech.insight.kilsme.rpc.register.ServiceMetadata;

import java.lang.reflect.Method;

@Data
public class RpcCallMetrics {
    //是否成功
    private boolean complete;
    private Throwable throwable;
    //持续时间
    private long duration;
    //开始时间
    private long startTime;
    private ServiceMetadata serviceMetadata;
    private Object[]params;
    private Method method;
    private RpcCallMetrics(){

    }

    public static RpcCallMetrics createRpcCallMetrics(Method method,Object[]params,ServiceMetadata provider){
        RpcCallMetrics metrics = new RpcCallMetrics();
        metrics.startTime=System.currentTimeMillis();
        metrics.params=params;
        metrics.method=method;
        metrics.serviceMetadata=provider;
        return metrics;
    }
    public void complete(){
    this.complete=true;
    this.duration=System.currentTimeMillis()-startTime;
    }
    public void errorComplete(Throwable throwable){
        this.throwable=throwable;
        this.duration=System.currentTimeMillis()-startTime;
    }

}
