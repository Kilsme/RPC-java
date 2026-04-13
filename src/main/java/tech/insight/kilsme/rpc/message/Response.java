package tech.insight.kilsme.rpc.message;

import lombok.Data;

/**
 * RPC 响应体：当前仅携带调用结果。
 */
@Data
public class Response {
    // RPC 返回值（示例中只回传 add 结果）。
    Object res;
    // 业务状态码：200 成功，400 失败。
    int code;
    // 失败时的错误信息。
    String errorMessage;
    // 与 Request.requestId 对应，用于匹配响应。
    private int requestId;

    // 构造失败响应。
    public static Response fail(String errorMessage,int requestId) {
        Response response = new Response();
        response.errorMessage = errorMessage;
        response.code = 400;
        response.requestId=requestId;
        return response;
    }

    // 构造成功响应。
    public static Response success(Object res,int requestId) {
        Response response = new Response();
        response.res = res;
        response.code = 200;
        response.requestId=requestId;
        return response;
    }

}
