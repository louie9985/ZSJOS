package cn.iocoder.yudao.module.zsjos.service.account;

import cn.iocoder.yudao.framework.common.biz.system.permission.dto.DeptDataPermissionRespDTO;
import cn.iocoder.yudao.framework.common.enums.CommonStatusEnum;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import cn.iocoder.yudao.module.system.api.user.dto.AdminUserRespDTO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MediaAccountCalendarScopeServiceTest {
    @InjectMocks private MediaAccountCalendarScopeService service;
    @Mock private PermissionApi permissionApi;
    @Mock private AdminUserApi adminUserApi;

    @Test
    void queryOnlyReturnsCurrentUser() {
        var scope = service.resolve(20L);
        assertFalse(scope.all());
        assertEquals(Set.of(20L), scope.userIds());
        verify(permissionApi, never()).getDeptDataPermission(anyLong());
    }

    @Test
    void managedUsesOnlyEnabledUsersFromBoundedDepartmentScope() {
        doReturn(false).when(permissionApi).hasAnyPermissions(20L, MediaAccountCalendarScopeService.PERMISSION_QUERY_ALL);
        doReturn(true).when(permissionApi).hasAnyPermissions(20L, MediaAccountCalendarScopeService.PERMISSION_QUERY_MANAGED);
        when(permissionApi.getDeptDataPermission(20L)).thenReturn(new DeptDataPermissionRespDTO().setDeptIds(Set.of(10L)));
        when(adminUserApi.getUserListByDeptIds(Set.of(10L))).thenReturn(List.of(user(21L, 0), user(22L, 1)));
        assertEquals(Set.of(20L, 21L), service.resolve(20L).userIds());
    }

    @Test
    void unboundedDepartmentScopeDoesNotBecomeCalendarAllAccess() {
        doReturn(false).when(permissionApi).hasAnyPermissions(20L, MediaAccountCalendarScopeService.PERMISSION_QUERY_ALL);
        doReturn(true).when(permissionApi).hasAnyPermissions(20L, MediaAccountCalendarScopeService.PERMISSION_QUERY_MANAGED);
        when(permissionApi.getDeptDataPermission(20L)).thenReturn(new DeptDataPermissionRespDTO().setAll(true));
        var scope = service.resolve(20L);
        assertFalse(scope.all());
        assertEquals(Set.of(20L), scope.userIds());
        verifyNoInteractions(adminUserApi);
    }

    @Test
    void explicitQueryAllIsTheOnlyAllAccessPath() {
        when(permissionApi.hasAnyPermissions(20L, MediaAccountCalendarScopeService.PERMISSION_QUERY_ALL)).thenReturn(true);
        assertTrue(service.resolve(20L).all());
        verify(permissionApi, never()).getDeptDataPermission(anyLong());
    }

    private static AdminUserRespDTO user(Long id, Integer status) {
        AdminUserRespDTO user = new AdminUserRespDTO(); user.setId(id); user.setStatus(status); return user;
    }
}
