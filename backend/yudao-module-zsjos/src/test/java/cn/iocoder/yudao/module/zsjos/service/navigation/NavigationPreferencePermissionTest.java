package cn.iocoder.yudao.module.zsjos.service.navigation;

import cn.iocoder.yudao.module.zsjos.controller.admin.navigation.NavigationPreferenceController;
import cn.iocoder.yudao.module.zsjos.controller.admin.navigation.vo.NavigationPreferenceVO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.navigation.NavigationPreferenceMapper;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.HashSet;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class NavigationPreferencePermissionTest {
    public static class Gate {
        final Set<String> permissions = new HashSet<>();
        public boolean hasPermission(String permission) { return permissions.contains(permission); }
    }
    @Configuration @EnableMethodSecurity
    static class Config {
        @Bean("ss") Gate gate() { return new Gate(); }
        @Bean NavigationPreferenceService service() { return mock(NavigationPreferenceService.class); }
        @Bean NavigationPreferenceMapper mapper() { return mock(NavigationPreferenceMapper.class); }
        @Bean NavigationPreferenceController controller(NavigationPreferenceService service) {
            var controller = new NavigationPreferenceController();
            ReflectionTestUtils.setField(controller, "service", service);
            return controller;
        }
    }
    @Test void queryAndSaveRequireConfiguredPermissionsBeforeCallingService() {
        try (var context = new AnnotationConfigApplicationContext(Config.class)) {
            SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken("fixture", "", "USER"));
            var controller = context.getBean(NavigationPreferenceController.class);
            var service = context.getBean(NavigationPreferenceService.class);
            var gate = context.getBean(Gate.class);
            var value = new NavigationPreferenceVO();
            assertThrows(AccessDeniedException.class, () -> controller.get("admin"));
            assertThrows(AccessDeniedException.class, () -> controller.save("admin", value));
            verifyNoInteractions(service);
            gate.permissions.add("zsjos:navigation-preference:query");
            when(service.get("admin")).thenReturn(value);
            assertSame(value, controller.get("admin").getData());
            assertThrows(AccessDeniedException.class, () -> controller.save("admin", value));
            verify(service, never()).save(anyString(), any());
            gate.permissions.add("zsjos:navigation-preference:update");
            assertTrue(controller.save("admin", value).getData());
            verify(service).save("admin", value);
            gate.permissions.remove("zsjos:navigation-preference:query");
            assertThrows(AccessDeniedException.class, () -> controller.save("admin", value));
        } finally { SecurityContextHolder.clearContext(); }
    }
}
