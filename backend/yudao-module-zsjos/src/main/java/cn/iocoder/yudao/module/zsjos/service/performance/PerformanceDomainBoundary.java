package cn.iocoder.yudao.module.zsjos.service.performance;

import java.lang.annotation.*;
@Target(ElementType.TYPE) @Retention(RetentionPolicy.RUNTIME)
public @interface PerformanceDomainBoundary { String value(); }
