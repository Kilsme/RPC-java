package tech.insight.kilsme.rpc.message;

import lombok.Data;

import java.io.Serializable;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * RPC 请求体。
 * <p>
 * 请求中包含服务名、方法名、参数类型、参数值等调用元信息。
 * Provider 在收到请求后，会根据这些字段完成反射定位并执行目标方法。
 * </p>
 */
@Data
public class Request implements Serializable {
    // 进程内自增请求号，用于请求-响应匹配。
    // Consumer 端会把它作为 in-flight 表的 key，Provider 回包时也会带回来。
    private static final AtomicInteger REQUEST_COUNTER=new AtomicInteger();
    // 每个请求创建时分配一个 requestId。
    private int requestId=REQUEST_COUNTER.getAndIncrement();
    // 目标服务名（示例中仅作演示）。
    // 一般就是接口的全限定名，例如 tech.insight.kilsme.rpc.api.Add。
    private String serviceName;
    // 目标方法名。
    private String methodName;
    // 参数类型列表（与 params 下标一一对应）。
    // Provider 反射调用时，必须依赖它准确找到目标方法。
    private Class<?>[]paramsClass;
    // 参数值列表。
    // 与 paramsClass 一起构成完整的反射入参信息。
    private   Object[]params;
    private boolean  genericInvoke;//判断是否是返回调用
    private String[] paramsClassStr;//泛化调用时参数类型列表

}
