package tech.insight.kilsme.rpc.message;

import lombok.Data;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * RPC 请求体：描述调用哪个服务、哪个方法以及入参。
 */
@Data
public class Request {
    private static final AtomicInteger   REQUEST_COUNTER=new AtomicInteger();
    private int requestId=REQUEST_COUNTER.getAndIncrement();
    // 目标服务名（示例中仅作演示）。
    private String serviceName;
    // 目标方法名。
    private String methodName;
    // 参数类型列表（与 params 下标一一对应）。
    private Class<?>[]paramsClass;
    // 参数值列表。
    private   Object[]params;
}
