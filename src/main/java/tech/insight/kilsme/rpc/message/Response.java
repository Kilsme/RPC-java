package tech.insight.kilsme.rpc.message;

import lombok.Data;

@Data
public class Response {
	// RPC 返回值（示例中只回传 add 结果）。
	Object res;
}
