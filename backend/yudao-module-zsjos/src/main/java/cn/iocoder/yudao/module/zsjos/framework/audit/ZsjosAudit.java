package cn.iocoder.yudao.module.zsjos.framework.audit;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Declares the audit treatment for an endpoint or a non-HTTP business operation. */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface ZsjosAudit {

    Mode mode() default Mode.WRITE;

    String action() default "";

    String targetType() default "";

    enum Mode {
        WRITE,
        READ_ONLY,
        SENSITIVE_READ
    }
}
