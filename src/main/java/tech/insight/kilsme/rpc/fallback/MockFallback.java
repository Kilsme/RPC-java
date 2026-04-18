package tech.insight.kilsme.rpc.fallback;

import tech.insight.kilsme.rpc.exception.RpcException;
import tech.insight.kilsme.rpc.metrices.RpcCallMetrics;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class MockFallback implements  Fallback{
    private final Map<Class<?>,Object> mockObjects=new ConcurrentHashMap<>();
    @Override
    public Object fallback(RpcCallMetrics metrics) throws InvocationTargetException, IllegalAccessException {
        Method method = metrics.getMethod();
        RpcFallback annotation = method.getDeclaringClass().getAnnotation(RpcFallback.class);//通过类去查看有没有添加rpcfallback注解
        if(annotation==null){
            throw new RpcException("没招了，降级还不行");
        }
        Class<?>methodClass=annotation.value();
        if(!method.getDeclaringClass().isAssignableFrom(methodClass)){
            throw new RpcException("降级类不合法");
        }
        Object mockobject = mockObjects.computeIfAbsent(methodClass, this::createMockObject);
        return method.invoke(mockobject,metrics.getParams());//传递了具体的值
    }
    private Object createMockObject(Class<?>methodClass){
      try {
          return methodClass.getConstructor().newInstance();
      }catch (Exception e){
          throw new RpcException("降级类必须有无参构造器,创建对象失败",e);
      }
    }
}
