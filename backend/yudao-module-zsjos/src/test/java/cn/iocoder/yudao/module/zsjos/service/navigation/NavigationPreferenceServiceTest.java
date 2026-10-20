package cn.iocoder.yudao.module.zsjos.service.navigation;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.zsjos.controller.admin.navigation.vo.NavigationPreferenceVO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.navigation.NavigationPreferenceMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NavigationPreferenceServiceTest {
    @Mock NavigationPreferenceMapper mapper;
    @InjectMocks NavigationPreferenceService service;
    @AfterEach void clear() { TenantContextHolder.clear(); }
    @Test void saveUsesOnlyAuthenticatedOwnerAndTenant() {
        try (var security = mockStatic(SecurityFrameworkUtils.class)) {
            security.when(SecurityFrameworkUtils::getLoginUserId).thenReturn(7L);
            TenantContextHolder.setTenantId(1L);
            service.save("admin", new NavigationPreferenceVO());
            verify(mapper).savePreference(eq(1L), eq(7L), eq("admin"), contains("groups"));
        }
    }
    @Test void readIsScopedToCurrentAccountAndClient() {
        try (var security = mockStatic(SecurityFrameworkUtils.class)) {
            security.when(SecurityFrameworkUtils::getLoginUserId).thenReturn(8L);
            assertTrue(service.get("workbench").getGroups().isEmpty());
            verify(mapper).selectPreference(8L, "workbench");
        }
    }
    @Test void anonymousCannotReadOrWrite() {
        try (var security = mockStatic(SecurityFrameworkUtils.class)) {
            security.when(SecurityFrameworkUtils::getLoginUserId).thenReturn(null);
            assertThrows(ServiceException.class, () -> service.get("admin"));
            assertThrows(ServiceException.class, () -> service.save("admin", new NavigationPreferenceVO()));
            verifyNoInteractions(mapper);
        }
    }
    @Test void rejectsUnknownClient() {
        assertThrows(ServiceException.class, () -> service.get("other"));
        verifyNoInteractions(mapper);
    }
    @Test void rejectsDuplicateKeysAndParents() {
        var preference = new NavigationPreferenceVO();
        var group = new NavigationPreferenceVO.Group();
        group.setParentKey("root"); group.setKeys(List.of("menu:1", "menu:1"));
        preference.setGroups(List.of(group));
        assertThrows(ServiceException.class, () -> NavigationPreferenceService.validate(preference));
        group.setKeys(List.of("menu:1")); preference.setGroups(List.of(group, group));
        assertThrows(ServiceException.class, () -> NavigationPreferenceService.validate(preference));
    }
    @Test void acceptsResetAndRejectsOversizedInput() {
        assertDoesNotThrow(() -> NavigationPreferenceService.validate(new NavigationPreferenceVO()));
        var preference = new NavigationPreferenceVO();
        var group = new NavigationPreferenceVO.Group();
        group.setParentKey("root"); group.setKeys(List.of("x".repeat(161)));
        preference.setGroups(List.of(group));
        assertThrows(ServiceException.class, () -> NavigationPreferenceService.validate(preference));
    }
}
