package tech.insight.kilsme.rpc.message;

import lombok.Data;

import java.io.Serializable;
@Data
public class HeartbeatResponse implements Serializable {
         private final long requestTime;
}
