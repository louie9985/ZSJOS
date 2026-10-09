package cn.iocoder.yudao.module.zsjos.service.lead;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.module.system.api.dept.DeptApi;
import cn.iocoder.yudao.module.system.api.dept.dto.DeptRespDTO;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import cn.iocoder.yudao.module.system.api.user.dto.AdminUserRespDTO;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.List;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SubordinateSalesObjectPermissionProviderTest {
    @Test void onlySystemManagedDepartmentsAndDescendantsGrantAccess() {
        var departments = mock(DeptApi.class);
        var users = mock(AdminUserApi.class);
        var scope = new LeadObjectPermissionService();
        ReflectionTestUtils.setField(scope, "deptApi", departments);
        ReflectionTestUtils.setField(scope, "adminUserApi", users);
        var provider = new SubordinateSalesObjectPermissionProvider();
        ReflectionTestUtils.setField(provider, "permissionService", scope);
        var parent = new DeptRespDTO(); parent.setId(100L);
        var child = new DeptRespDTO(); child.setId(101L);
        var sales = new AdminUserRespDTO(); sales.setId(20L);
        when(departments.getDeptListByLeaderUserId(10L)).thenReturn(List.of(parent));
        when(departments.getChildDeptList(100L)).thenReturn(List.of(child));
        when(users.getUserListByDeptIds(Set.of(100L, 101L))).thenReturn(List.of(sales));
        assertTrue(provider.hasPermission(20L, "newcomer-pool", 10L));
        assertThrows(ServiceException.class, () -> provider.check(21L, "newcomer-pool", 10L));
        assertFalse(provider.hasPermission(20L, "newcomer-pool", 99L));
        assertFalse(provider.hasPermission(20L, "read", 10L));
        assertFalse(provider.hasPermission(20L, "newcomer-pool", null));
        // An empty System result (including another tenant) cannot inherit an earlier scope.
        when(departments.getDeptListByLeaderUserId(10L)).thenReturn(List.of());
        assertThrows(ServiceException.class, () -> provider.check(20L, "newcomer-pool", 10L));
    }
}
