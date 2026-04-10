package tech.insight.kilsme.rpc.provider;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 服务注册中心：维护“接口名 -> 服务实例包装器”的映射。
 */
public class ProviderRegistry {
    // key 是接口全限定名，value 是可执行的服务封装。
    private Map<String, invocation<?>> serviceInstanceMap=new ConcurrentHashMap<>();
    // 注册服务实现，要求必须按接口维度注册。
    public<I> void register(Class<I>interfaceClass,I serviceInstance){
           if(!interfaceClass.isInterface()){
               throw  new IllegalArgumentException("只能注册接口类型");
           }
        if (serviceInstanceMap.putIfAbsent(interfaceClass.getName(),new invocation<>(interfaceClass,serviceInstance))!=null) {
            throw new IllegalStateException("服务已注册: "+interfaceClass.getName());
        }
    }
    public invocation<?> findService(String serviceName){
        return serviceInstanceMap.get(serviceName);
    }

    /**
     * 服务实例包装器：保存接口类型和实现实例，并负责反射调用。
     */
    public static class invocation<I> {
        final I serviceInstance;
        final Class<I>interfaceClass;
     //这个invocation将示例和说明书打包在一起
        public invocation(Class<I>interfaceClass, I serviceInstance){
            this.serviceInstance=serviceInstance;
            this.interfaceClass = interfaceClass;
        }
        // 基于接口方法签名定位目标方法并执行。
        public Object invoke(String methodName,Class<?>[] paramTypes,Object[] params)throws Exception {
            //使用接口interfaceClass的形式去寻找方法名字，只会找到公开暴露的接口方法
         Method invokeMethod=interfaceClass.getDeclaredMethod(methodName,paramTypes);
         return invokeMethod.invoke(serviceInstance,params);
        }

    }
}
