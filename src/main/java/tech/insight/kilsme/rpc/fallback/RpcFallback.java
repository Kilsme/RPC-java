package tech.insight.kilsme.rpc.fallback;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

@Target(ElementType.TYPE)//这个注解可以放到类上
@Retention(java.lang.annotation.RetentionPolicy.RUNTIME)//运行时保留，可以通过反射获取
public @interface RpcFallback {
   Class<?> value();

}

