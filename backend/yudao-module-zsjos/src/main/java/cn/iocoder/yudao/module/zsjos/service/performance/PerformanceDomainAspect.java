package cn.iocoder.yudao.module.zsjos.service.performance;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.*;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Aspect @Component @Order(org.springframework.core.Ordered.HIGHEST_PRECEDENCE)
public class PerformanceDomainAspect {
    @Around("@within(cn.iocoder.yudao.module.zsjos.service.performance.PerformanceDomainBoundary)")
    public Object within(ProceedingJoinPoint point) throws Throwable {
        var boundary = org.springframework.core.annotation.AnnotationUtils.findAnnotation(
                org.springframework.aop.support.AopUtils.getTargetClass(point.getTarget()), PerformanceDomainBoundary.class);
        if (boundary == null) throw new IllegalStateException("Missing performance domain boundary");
        String previous = PerformanceDomain.enter(boundary.value());
        try { return point.proceed(); } finally { PerformanceDomain.restore(previous); }
    }
}
