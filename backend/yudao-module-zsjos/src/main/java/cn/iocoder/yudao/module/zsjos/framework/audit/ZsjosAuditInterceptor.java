package cn.iocoder.yudao.module.zsjos.framework.audit;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.web.core.util.WebFrameworkUtils;
import cn.iocoder.yudao.module.zsjos.service.audit.BusinessAuditService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import java.lang.reflect.Method;
import java.util.Locale;

/** Audits every ZSJOS state-changing HTTP endpoint unless it is explicitly declared read-only. */
public class ZsjosAuditInterceptor implements HandlerInterceptor {

    private static final String AUDIT_ID_ATTRIBUTE = ZsjosAuditInterceptor.class.getName() + ".auditId";
    private static final String START_NANOS_ATTRIBUTE = ZsjosAuditInterceptor.class.getName() + ".startNanos";

    private final BusinessAuditService auditService;

    public ZsjosAuditInterceptor(BusinessAuditService auditService) {
        this.auditService = auditService;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!(handler instanceof HandlerMethod method) || !isZsjosController(method)) {
            return true;
        }
        ZsjosAudit annotation = AnnotatedElementUtils.findMergedAnnotation(method.getMethod(), ZsjosAudit.class);
        if (annotation == null) {
            annotation = AnnotatedElementUtils.findMergedAnnotation(method.getBeanType(), ZsjosAudit.class);
        }
        if (!mustAudit(request.getMethod(), method.getMethod(), annotation)) {
            return true;
        }
        String action = annotation != null && StrUtil.isNotBlank(annotation.action())
                ? annotation.action() : stableName(method.getBeanType().getSimpleName(), method.getMethod().getName());
        String targetType = annotation != null && StrUtil.isNotBlank(annotation.targetType())
                ? annotation.targetType() : stableTargetType(method.getBeanType().getSimpleName());
        String category = annotation != null && annotation.mode() == ZsjosAudit.Mode.SENSITIVE_READ
                ? "sensitive_read" : "business";
        String sourceType = sourceType(method.getBeanType());
        Long auditId = auditService.begin(new ZsjosAuditOperation(category, action, targetType, null,
                sourceType, request.getMethod(), request.getRequestURI()));
        request.setAttribute(AUDIT_ID_ATTRIBUTE, auditId);
        request.setAttribute(START_NANOS_ATTRIBUTE, System.nanoTime());
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        Object id = request.getAttribute(AUDIT_ID_ATTRIBUTE);
        if (!(id instanceof Long auditId)) {
            return;
        }
        CommonResult<?> result = WebFrameworkUtils.getCommonResult(request);
        boolean success = ex == null && (result == null || result.isSuccess());
        Integer resultCode = result != null ? result.getCode() : response.getStatus();
        String resultMessage = result != null ? result.getMsg() : exceptionSummary(ex);
        Object start = request.getAttribute(START_NANOS_ATTRIBUTE);
        long duration = start instanceof Long startNanos ? (System.nanoTime() - startNanos) / 1_000_000L : 0L;
        auditService.complete(auditId, success, resultCode, resultMessage, duration);
    }

    static boolean mustAudit(String httpMethod, Method handlerMethod, ZsjosAudit annotation) {
        if (annotation != null && annotation.mode() == ZsjosAudit.Mode.READ_ONLY) {
            return false;
        }
        if (annotation != null && annotation.mode() == ZsjosAudit.Mode.SENSITIVE_READ) {
            return true;
        }
        if (annotation != null && annotation.mode() == ZsjosAudit.Mode.WRITE) {
            return true;
        }
        if ("GET".equals(httpMethod) || "HEAD".equals(httpMethod) || "OPTIONS".equals(httpMethod)) {
            return false;
        }
        return true;
    }

    private static boolean isZsjosController(HandlerMethod method) {
        return method.getBeanType().getPackageName().startsWith("cn.iocoder.yudao.module.zsjos.controller.");
    }

    static String sourceType(Class<?> controllerType) {
        String packageName = controllerType.getPackageName();
        if (packageName.contains(".controller.app.partner")) {
            return "PARTNER";
        }
        if (packageName.contains(".controller.pub")) {
            return "PUBLIC_CALLBACK";
        }
        return "ADMIN";
    }

    private static String exceptionSummary(Exception exception) {
        if (exception == null) {
            return null;
        }
        return exception instanceof ServiceException ? exception.getMessage() : exception.getClass().getSimpleName();
    }

    private static String stableName(String controllerName, String methodName) {
        return stableTargetType(controllerName) + "." + camelToKebab(methodName);
    }

    private static String stableTargetType(String controllerName) {
        return camelToKebab(StrUtil.removeSuffix(controllerName, "Controller"));
    }

    private static String camelToKebab(String value) {
        return value.replaceAll("([a-z0-9])([A-Z])", "$1-$2").toLowerCase(Locale.ROOT);
    }
}
