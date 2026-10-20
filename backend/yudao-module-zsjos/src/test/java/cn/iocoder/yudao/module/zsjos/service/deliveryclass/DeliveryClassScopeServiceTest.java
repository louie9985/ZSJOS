package cn.iocoder.yudao.module.zsjos.service.deliveryclass;

import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import cn.iocoder.yudao.module.system.api.user.dto.AdminUserRespDTO;
import cn.iocoder.yudao.framework.common.biz.system.permission.dto.DeptDataPermissionRespDTO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.deliveryclass.DeliveryClassDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.deliveryclass.DeliveryClassMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DeliveryClassScopeServiceTest {
    @InjectMocks DeliveryClassScopeService service;
    @Mock PermissionApi permissionApi;
    @Mock DeliveryClassMapper classMapper;
    @Mock AdminUserApi adminUserApi;

    @Test void selfReadNeverResolvesTheCurrentDepartmentButCommandScopeIsPreserved() {
        var permission = new DeptDataPermissionRespDTO(); permission.setSelf(true);
        when(permissionApi.getDeptDataPermission(7L)).thenReturn(permission);
        assertFalse(service.resolveRead(7L).allDepartments());
        assertTrue(service.resolveRead(7L).deptIds().isEmpty());
        assertFalse(service.containsRead(7L, 20L));
        verifyNoInteractions(adminUserApi);
        when(adminUserApi.getUser(7L)).thenReturn(new AdminUserRespDTO().setDeptId(20L));
        assertTrue(service.contains(7L, 20L));
    }

    @Test void configuredAndMergedDepartmentGrantsAreConsumedWithoutAddingSelfDepartment() {
        for (var departments : java.util.List.of(java.util.Set.of(20L), java.util.Set.of(20L, 21L), java.util.Set.of(40L, 50L))) {
            var permission = new DeptDataPermissionRespDTO();
            permission.setSelf(true); permission.setDeptIds(departments);
            when(permissionApi.getDeptDataPermission(7L)).thenReturn(permission);
            assertEquals(departments, service.resolveRead(7L).deptIds());
            assertFalse(service.containsRead(7L, 99L));
        }
        verifyNoInteractions(adminUserApi);
    }

    @Test void allEmptyAndFailedReadScopesNeverFallBackToAUsersDepartment() {
        var all = new DeptDataPermissionRespDTO(); all.setAll(true);
        when(permissionApi.getDeptDataPermission(7L)).thenReturn(all).thenReturn(null);
        assertTrue(service.resolveRead(7L).allDepartments());
        assertEquals(java.util.Set.of(), service.resolveRead(7L).deptIds());
        when(permissionApi.getDeptDataPermission(7L)).thenThrow(new IllegalStateException("scope unavailable"));
        assertThrows(IllegalStateException.class, () -> service.resolveRead(7L));
        verifyNoInteractions(adminUserApi);
    }

    @Test void ordinaryReaderCannotResolvePendingScope() {
        assertNull(service.readablePendingClassId(7L));
        verifyNoInteractions(classMapper);
    }
    @Test void managerUsesActualTenantClassAndMissingClassFailsClosed() {
        when(permissionApi.hasAnyPermissions(7L, DeliveryClassService.PERMISSION_QUERY_MANAGED)).thenReturn(true);
        when(classMapper.selectPending()).thenReturn(new DeliveryClassDO().setId(90L)).thenReturn(null);
        assertEquals(90L, service.readablePendingClassId(7L));
        assertNull(service.readablePendingClassId(7L));
    }
}
