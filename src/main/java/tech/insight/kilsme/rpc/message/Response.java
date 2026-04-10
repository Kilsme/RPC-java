package tech.insight.kilsme.rpc.message;

import lombok.Data;

/**
 * RPC 响应体：当前仅携带调用结果。
 */
@Data
public class Response {
    // RPC 返回值（示例中只回传 add 结果）。
    Object res;
}
