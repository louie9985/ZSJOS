package cn.iocoder.yudao.module.zsjos.framework.audit;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.module.zsjos.service.audit.BusinessAuditService;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;

import java.util.Locale;

/** Applies the same attempt/result audit protocol to scheduled jobs and asynchronous callbacks. */
@Aspect
@Component
public class ZsjosAuditAspect {

    private final BusinessAuditService auditService;

    public ZsjosAuditAspect(BusinessAuditService auditService) {
        this.auditService = auditService;
    }

    @Around("@annotation(audit)")
    public Object audit(ProceedingJoinPoint joinPoint, ZsjosAudit audit) throws Throwable {
        if (audit.mode() == ZsjosAudit.Mode.READ_ONLY) {
            return joinPoint.proceed();
        }
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        if (signature.getDeclaringType().getPackageName()
                .startsWith("cn.iocoder.yudao.module.zsjos.controller.")) {
            return joinPoint.proceed();
        }
        String owner = signature.getDeclaringType().getSimpleName();
        String action = StrUtil.isNotBlank(audit.action()) ? audit.action()
                : camelToKebab(owner) + "." + camelToKebab(signature.getMethod().getName());
        String targetType = StrUtil.isNotBlank(audit.targetType()) ? audit.targetType() : camelToKebab(owner);
        long started = System.nanoTime();
        Long auditId = auditService.begin(new ZsjosAuditOperation("system", action, targetType, null,
                "SYSTEM", null, null));
        try {
            Object result = joinPoint.proceed();
            auditService.complete(auditId, true, 0, null, elapsedMillis(started));
            return result;
        } catch (Throwable ex) {
            Integer code = ex instanceof ServiceException serviceException ? serviceException.getCode() : 500;
            String message = ex instanceof ServiceException ? ex.getMessage() : ex.getClass().getSimpleName();
            auditService.complete(auditId, false, code, message, elapsedMillis(started));
            throw ex;
        }
    }

    private static long elapsedMillis(long started) {
        return (System.nanoTime() - started) / 1_000_000L;
    }

    private static String camelToKebab(String value) {
        return value.replaceAll("([a-z0-9])([A-Z])", "$1-$2").toLowerCase(Locale.ROOT);
    }
}
