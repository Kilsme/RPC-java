package tech.insight.kilsme.rpc.register;

import lombok.Data;

@Data
public class RegisterConfig {
    private  String registerType="zookeeper";
    private String connectString;
}
