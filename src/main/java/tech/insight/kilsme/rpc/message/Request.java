package tech.insight.kilsme.rpc.message;

import lombok.Data;

@Data
public class Request {
    // 目标服务名（示例中仅作演示）。
    private String serviceName;
    // 目标方法名。
    private String methodName;
    // 参数类型列表（与 params 下标一一对应）。
    private String[]paramsClass;
    // 参数值列表。
    private   Object[]params;
}
