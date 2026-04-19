package tech.insight.kilsme.rpc.spi;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

@Target(java.lang.annotation.ElementType.TYPE)
@Retention(java.lang.annotation.RetentionPolicy.RUNTIME)
public @interface Spi {
    String value();
    int code() default -1;

}
