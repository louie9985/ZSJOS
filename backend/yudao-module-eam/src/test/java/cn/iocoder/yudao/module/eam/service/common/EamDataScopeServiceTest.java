package cn.iocoder.yudao.module.eam.service.common;

import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import cn.iocoder.yudao.module.system.api.dept.DeptApi;
import cn.iocoder.yudao.module.hrm.api.employee.HrmEmployeeApi;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EamDataScopeServiceTest {
    @Mock PermissionApi permissionApi;
    @Mock AdminUserApi adminUserApi;
    @Mock DeptApi deptApi;
    @Mock HrmEmployeeApi employeeApi;
    @InjectMocks EamDataScopeService service;

    @Test void readAllDoesNotRequireAnEmployeeOrDepartmentRelationship() {
        when(permissionApi.hasTenantReadAllAccess(9L)).thenReturn(true);
        assertTrue(service.resolve(9L, EamDataScopeService.ASSET_QUERY_SELF, EamDataScopeService.ASSET_QUERY_DEPT).all());
        verifyNoInteractions(adminUserApi, deptApi, employeeApi);
        verify(permissionApi, never()).hasAnyPermissions(9L, EamDataScopeService.MANAGE_ALL);
    }

    @Test void ordinaryUserWithoutScopeKeepsAnEmptyScope() {
        var scope = service.resolve(9L, EamDataScopeService.ASSET_QUERY_SELF, EamDataScopeService.ASSET_QUERY_DEPT);
        assertFalse(scope.all()); assertFalse(scope.self()); assertTrue(scope.deptIds().isEmpty());
    }
}
