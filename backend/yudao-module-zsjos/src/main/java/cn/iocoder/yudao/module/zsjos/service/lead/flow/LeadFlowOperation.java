package cn.iocoder.yudao.module.zsjos.service.lead.flow;

import java.lang.annotation.*;

/** A successful business command and its persisted Lead changes form one timeline operation. */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface LeadFlowOperation {
    String value();
    String target() default "lead";
    int id() default 0;
    int actor() default 1;
    String actorType() default "ADMIN";
}
