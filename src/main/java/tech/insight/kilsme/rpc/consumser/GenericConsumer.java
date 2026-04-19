package tech.insight.kilsme.rpc.consumser;

public interface GenericConsumer {
    Object $invoke(String serviceName,String methodName,String[]paramsType,Object[]args);
}
