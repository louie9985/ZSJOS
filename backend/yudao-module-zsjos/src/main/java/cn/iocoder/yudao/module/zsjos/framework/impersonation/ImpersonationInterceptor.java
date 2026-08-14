package cn.iocoder.yudao.module.zsjos.framework.impersonation;

import cn.iocoder.yudao.framework.security.core.LoginUser;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.module.zsjos.service.impersonation.ImpersonationService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Map;
import java.util.HashMap;
import java.util.Set;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants.IMPERSONATION_READ_ONLY;

@Component
public class ImpersonationInterceptor implements HandlerInterceptor {
    public static final String HEADER = "X-ZSJOS-Impersonation-Session";
    private static final Set<String> SAFE_METHODS = Set.of("GET", "HEAD", "OPTIONS");
    private final ImpersonationService service;

    public ImpersonationInterceptor(ImpersonationService service) {
        this.service = service;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String value = request.getHeader(HEADER);
        if (value == null || value.isBlank()) return true;
        if (!SAFE_METHODS.contains(request.getMethod())) throw exception(IMPERSONATION_READ_ONLY);
        LoginUser administrator = SecurityFrameworkUtils.getLoginUser();
        if (administrator == null) return true;
        if (administrator.getVisitTenantId() != null
                && !administrator.getVisitTenantId().equals(administrator.getTenantId())) {
            throw exception(IMPERSONATION_READ_ONLY);
        }
        Long sessionId;
        try {
            sessionId = Long.valueOf(value);
        } catch (NumberFormatException ignored) {
            throw exception(IMPERSONATION_READ_ONLY);
        }
        var context = service.useReadSession(administrator.getId(), sessionId,
                request.getMethod(), request.getRequestURI());
        administrator.setContext("zsjos.impersonation.originalUserId", administrator.getId());
        administrator.setContext("zsjos.impersonation.sessionId", sessionId);
        administrator.setId(context.targetUserId());
        Map<String, String> targetInfo = new HashMap<>();
        targetInfo.put(LoginUser.INFO_KEY_NICKNAME, context.targetName());
        if (context.targetDeptId() != null) {
            targetInfo.put(LoginUser.INFO_KEY_DEPT_ID, context.targetDeptId().toString());
        }
        administrator.setInfo(targetInfo);
        SecurityFrameworkUtils.setLoginUser(administrator, request);
        return true;
    }
}
