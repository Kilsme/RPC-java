package tech.insight.kilsme.rpc.provider;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 服务注册中心：维护“接口名 -> 服务实例包装器”的映射。
 *
 * <p>注意这里的“注册中心”是 Provider 本地内存里的注册表，不是 Zookeeper。
 * 它的作用是：Provider 收到请求后，能够根据接口名快速找到对应实现并反射执行。</p>
 */
public class ProviderRegistry {
    // key 是接口全限定名，value 是可执行的服务封装。
    // 之所以按接口名保存，是因为 RPC 对外暴露的是接口契约，而不是具体实现类。
    private  final Map<String, invocation<?>> serviceInstanceMap=new ConcurrentHashMap<>();
    /**
     * 注册服务实现，要求必须按接口维度注册。
     *
     * @param interfaceClass 服务接口类型（例如 Add.class）
     * @param serviceInstance 接口实现对象
     */
    public<I> void register(Class<I>interfaceClass,I serviceInstance){
              // 仅允许接口维度注册，避免直接暴露具体实现类。
              // 这样可以强制 Provider 的发布方式更贴近 RPC 的“面向接口编程”。
           if(!interfaceClass.isInterface()){
               throw  new IllegalArgumentException("只能注册接口类型");
           }
        if (serviceInstanceMap.putIfAbsent(interfaceClass.getName(),new invocation<>(interfaceClass,serviceInstance))!=null) {
            throw new IllegalStateException("服务已注册: "+interfaceClass.getName());
        }
    }

    /**
     * 按服务名查找对应实例包装器。
     *
     * <p>这个 serviceName 一般就是 Request 里传来的接口全限定名。</p>
     */
    public invocation<?> findService(String serviceName){
        // serviceName 一般来自 Request.serviceName。
        return serviceInstanceMap.get(serviceName);
    }
    /**
     * 返回已注册的全部服务名，用于启动时批量注册到注册中心。
     *
     * <p>ProviderServer 启动后会调用这里，把所有本地服务统一发布到 Zookeeper。</p>
     */
    public List<String> allServiceName(){
           return  new ArrayList<>(this.serviceInstanceMap.keySet()) ;
    }

    /**
     * 服务实例包装器：保存接口类型和实现实例，并负责反射调用。
     *
     * <p>可以把它理解为“接口 + 实现”的组合体。</p>
     */
    public static class invocation<I> {
        final I serviceInstance;
        final Class<I>interfaceClass;
     // 这个 invocation 将“实现实例 + 接口方法签名”绑定在一起。
        public invocation(Class<I>interfaceClass, I serviceInstance){
            this.serviceInstance=serviceInstance;
            this.interfaceClass = interfaceClass;
        }
        /**
         * 基于接口方法签名定位目标方法并执行。
         *
         * <p>这里不直接调用实现类的方法名，而是先通过接口类查找方法，再在实现对象上执行，
         * 这样能保证只暴露接口中定义的公开方法。</p>
         *
         * @param methodName 方法名
         * @param paramTypes 参数类型列表
         * @param params 参数值列表
         */
        public Object invoke(String methodName,Class<?>[] paramTypes,Object[] params)throws Exception {
            // 使用 interfaceClass 去寻找方法，只会匹配接口里公开暴露的方法。
            Method invokeMethod=interfaceClass.getDeclaredMethod(methodName,paramTypes);
            // 在实现类实例上执行该接口方法。
            return invokeMethod.invoke(serviceInstance,params);
        }

    }
}
