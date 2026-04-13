package tech.insight.kilsme.rpc.register;

import java.util.List;

public interface ServiceRegister {
    //查询可用的provider
    //注册服务
    void init(RegisterConfig registerConfig) throws Exception;
    void  registerService(ServiceMetadata metadata);
    //查询服务
    List<ServiceMetadata>fetchServiceList(String service) throws Exception;



}
