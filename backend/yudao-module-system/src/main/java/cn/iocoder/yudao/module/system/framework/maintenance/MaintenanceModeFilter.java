package cn.iocoder.yudao.module.system.framework.maintenance;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.util.servlet.ServletUtils;
import cn.iocoder.yudao.module.system.api.maintenance.MaintenanceModeApi;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Set;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 600)
public class MaintenanceModeFilter extends OncePerRequestFilter {
    private static final Set<String> SAFE_METHODS = Set.of("GET", "HEAD", "OPTIONS");
    private static final Set<String> WRITE_EXEMPT_SUFFIXES = Set.of(
            "/system/auth/login", "/system/auth/logout", "/system/auth/send-sms-code",
            "/system/auth/sms-login", "/system/auth/social-login", "/system/sms/callback",
            "/system/social-user/bind", "/system/maintenance-mode");
    private final MaintenanceModeApi maintenanceModeApi;

    public MaintenanceModeFilter(MaintenanceModeApi maintenanceModeApi) {
        this.maintenanceModeApi = maintenanceModeApi;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (!maintenanceModeApi.isEnabled() || isExempt(request)) {
            chain.doFilter(request, response);
            return;
        }
        response.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
        ServletUtils.writeJSON(response, CommonResult.error(503, "系统维护中，请稍后再试"));
    }

    private boolean isExempt(HttpServletRequest request) {
        if (SAFE_METHODS.contains(request.getMethod())) {
            return true;
        }
        String uri = request.getRequestURI();
        return WRITE_EXEMPT_SUFFIXES.stream().anyMatch(uri::endsWith);
    }
}
