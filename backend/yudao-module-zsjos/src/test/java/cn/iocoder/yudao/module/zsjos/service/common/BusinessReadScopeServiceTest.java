package cn.iocoder.yudao.module.zsjos.service.common;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import cn.iocoder.yudao.module.system.api.user.dto.AdminUserRespDTO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BusinessReadScopeServiceTest {
    @InjectMocks private BusinessReadScopeService service;
    @Mock private PermissionApi permissionApi;
    @Mock private AdminUserApi userApi;

    @Test void defaultsToSelfAndRejectsForgedScope() {
        assertEquals(10L, service.resolve(null, null, 10L));
        assertEquals(10L, service.resolve("SELF", null, 10L));
        assertThrows(ServiceException.class, () -> service.resolve("ALL", null, 10L));
        assertThrows(ServiceException.class, () -> service.resolve("USER", 20L, 10L));
        assertThrows(ServiceException.class, () -> service.resolve("SELF", 20L, 10L));
        assertThrows(ServiceException.class, () -> service.resolve("invalid", null, 10L));
        verifyNoInteractions(userApi);
    }

    @Test void administratorMustSelectExistingTenantUserAndCannotSupplyAmbiguousScope() {
        when(permissionApi.hasTenantReadAllAccess(10L)).thenReturn(true);
        assertNull(service.resolve("ALL", null, 10L));
        assertThrows(ServiceException.class, () -> service.resolve("ALL", 20L, 10L));
        assertThrows(ServiceException.class, () -> service.resolve("USER", null, 10L));
        assertThrows(ServiceException.class, () -> service.resolve("USER", 20L, 10L));
        when(userApi.getUser(20L)).thenReturn(new AdminUserRespDTO().setId(20L));
        assertEquals(20L, service.resolve("USER", 20L, 10L));
    }
}
