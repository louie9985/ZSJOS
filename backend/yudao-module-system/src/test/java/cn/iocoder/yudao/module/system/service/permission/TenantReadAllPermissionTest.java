package cn.iocoder.yudao.module.system.service.permission;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.system.dal.dataobject.permission.RoleDO;
import cn.iocoder.yudao.module.system.dal.dataobject.user.AdminUserDO;
import cn.iocoder.yudao.module.system.service.user.AdminUserService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TenantReadAllPermissionTest {
    @Spy @InjectMocks private PermissionServiceImpl service;
    @Mock private AdminUserService userService;

    @AfterEach void cleanup() { TenantContextHolder.clear(); org.springframework.security.core.context.SecurityContextHolder.clearContext(); }

    @Test void disabledRoleAndPartnerIdentityCannotGainAdministratorReading() {
        TenantContextHolder.setTenantId(1L);
        var user = new AdminUserDO(); user.setTenantId(1L); user.setStatus(0);
        when(userService.getUser(10L)).thenReturn(user);
        var role = new RoleDO(); role.setTenantId(1L); role.setCode("super_admin"); role.setStatus(1);
        doReturn(List.of(role)).when(service).getEnableUserRoleListByUserIdFromCache(10L);
        assertFalse(service.hasTenantReadAllAccess(10L));
        role.setStatus(0);
        cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils.setLoginUser(
                new cn.iocoder.yudao.framework.security.core.LoginUser().setId(10L)
                        .setUserType(cn.iocoder.yudao.framework.common.enums.UserTypeEnum.PARTNER.getValue()),
                new org.springframework.mock.web.MockHttpServletRequest());
        assertFalse(service.hasTenantReadAllAccess(10L));
    }

    @Test void bothAdministratorCodesGrantReadAndRevocationRemovesIt() {
        TenantContextHolder.setTenantId(1L);
        AdminUserDO user = new AdminUserDO(); user.setTenantId(1L); user.setStatus(0);
        when(userService.getUser(10L)).thenReturn(user);
        for (String code : List.of("super_admin", "system_administrator")) {
            RoleDO role = new RoleDO(); role.setTenantId(1L); role.setCode(code); role.setStatus(0);
            doReturn(List.of(role)).when(service).getEnableUserRoleListByUserIdFromCache(10L);
            assertTrue(service.hasTenantReadAllAccess(10L));
        }
        doReturn(List.of()).when(service).getEnableUserRoleListByUserIdFromCache(10L);
        assertFalse(service.hasTenantReadAllAccess(10L));
    }

    @Test void deniesMissingTenantDisabledUserAndForeignTenantUser() {
        assertFalse(service.hasTenantReadAllAccess(10L));
        TenantContextHolder.setTenantId(1L);
        assertFalse(service.hasTenantReadAllAccess(null));
        AdminUserDO user = new AdminUserDO(); user.setTenantId(2L); user.setStatus(0);
        when(userService.getUser(10L)).thenReturn(user);
        assertFalse(service.hasTenantReadAllAccess(10L));
        user.setTenantId(1L); user.setStatus(1);
        assertFalse(service.hasTenantReadAllAccess(10L));
        verify(service, never()).getEnableUserRoleListByUserIdFromCache(anyLong());
    }

    @Test void deniesForeignTenantRoleAndOrdinaryEffectiveSubject() {
        TenantContextHolder.setTenantId(1L);
        AdminUserDO user = new AdminUserDO(); user.setTenantId(1L); user.setStatus(0);
        when(userService.getUser(20L)).thenReturn(user);
        RoleDO role = new RoleDO(); role.setTenantId(2L); role.setCode("super_admin");
        doReturn(List.of(role)).when(service).getEnableUserRoleListByUserIdFromCache(20L);
        assertFalse(service.hasTenantReadAllAccess(20L));
        role.setTenantId(1L); role.setCode("sales_specialist");
        assertFalse(service.hasTenantReadAllAccess(20L));
        verify(userService, never()).getUser(10L);
    }
}
