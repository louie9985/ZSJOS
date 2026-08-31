package cn.iocoder.yudao.module.zsjos.framework.permission;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.METHOD;

@Target(METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface ZsjosPermission {
    String bizType();
    String bizId();
    String action();
}
