package tech.insight.kilsme.rpc.message;

import lombok.Data;

/**
 * RPC 响应体：当前仅携带调用结果。
 *
 * <p>Provider 端会把业务执行结果或者错误信息封装成这个对象，再通过 Netty 返回给 Consumer。</p>
 */
@Data
public class Response {
    // RPC 返回值（示例中只回传 add 结果）。
    // 当 code=200 时，这里通常就是业务返回值；当 code=400 时，这里一般为空。
    Object res;
    // 业务状态码：200 成功，400 失败。
    // Consumer 收到后可以先判断 code，再决定是直接返回 res 还是抛异常。
    int code;
    // 失败时的错误信息。
    // 这个字段通常会被抛到 Consumer 调用方，用于提示具体失败原因。
    String errorMessage;
    // 与 Request.requestId 对应，用于匹配响应。
    // 这是 Consumer 并发场景下完成 Future 的关键字段。
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
