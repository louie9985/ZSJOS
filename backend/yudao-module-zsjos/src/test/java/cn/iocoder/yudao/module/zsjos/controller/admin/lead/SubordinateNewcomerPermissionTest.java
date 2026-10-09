package cn.iocoder.yudao.module.zsjos.controller.admin.lead;

import cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.subordinate.SubordinateNewcomerPoolReqVO;
import cn.iocoder.yudao.module.zsjos.service.lead.SubordinateSalesService;
import org.junit.jupiter.api.Test;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.context.support.StaticApplicationContext;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.expression.method.DefaultMethodSecurityExpressionHandler;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.authorization.method.AuthorizationManagerBeforeMethodInterceptor;
import org.springframework.security.authorization.method.PreAuthorizeAuthorizationManager;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class SubordinateNewcomerPermissionTest {
    public static class Permissions {
        String granted = "";
        public boolean hasPermission(String value) { return value.equals(granted); }
    }

    @Test void queryPermissionDoesNotGrantConfiguration() {
        var permissions = new Permissions();
        try (var context = new StaticApplicationContext()) {
            context.getBeanFactory().registerSingleton("ss", permissions);
            context.refresh();
            var handler = new DefaultMethodSecurityExpressionHandler(); handler.setApplicationContext(context);
            var manager = new PreAuthorizeAuthorizationManager(); manager.setExpressionHandler(handler);
            var target = new SubordinateSalesController();
            var service = mock(SubordinateSalesService.class);
            ReflectionTestUtils.setField(target, "service", service);
            var factory = new ProxyFactory(target);
            factory.setProxyTargetClass(true);
            factory.addAdvisor(AuthorizationManagerBeforeMethodInterceptor.preAuthorize(manager));
            var controller = (SubordinateSalesController) factory.getProxy();
            SecurityContextHolder.getContext().setAuthentication(
                    UsernamePasswordAuthenticationToken.authenticated("fixture", "unused", List.of()));
            var request = new SubordinateNewcomerPoolReqVO(); request.setEnabled(true); request.setDailyQuota(5);
            permissions.granted = "zsjos:subordinate-sales:query";
            assertThrows(AccessDeniedException.class, () -> controller.updateNewcomerPool(10L, request));
            verifyNoInteractions(service);
            permissions.granted = "zsjos:subordinate-sales:newcomer-pool";
            assertTrue(controller.updateNewcomerPool(10L, request).getData());
            verify(service).updateNewcomerPool(eq(10L), same(request), nullable(Long.class));
        } finally {
            SecurityContextHolder.clearContext();
        }
    }
}
