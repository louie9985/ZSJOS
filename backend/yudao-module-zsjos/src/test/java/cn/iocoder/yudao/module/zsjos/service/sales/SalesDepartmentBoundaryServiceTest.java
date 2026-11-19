package cn.iocoder.yudao.module.zsjos.service.sales;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.common.biz.system.permission.dto.DeptDataPermissionRespDTO;
import cn.iocoder.yudao.module.system.api.dept.DeptApi;
import cn.iocoder.yudao.module.system.api.dept.dto.DeptRespDTO;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import cn.iocoder.yudao.module.system.api.user.dto.AdminUserRespDTO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.performance.PerformanceOrgDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.performance.PerformanceOrgMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Org: roots 10 (销售一部, child 11) and 20 (销售二部); 30 is a non-sales department; 1 is the company. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SalesDepartmentBoundaryServiceTest {
    @InjectMocks SalesDepartmentBoundaryService service;
    @Mock PerformanceOrgMapper organizations;
    @Mock DeptApi departments;
    @Mock PermissionApi permissions;
    @Mock AdminUserApi users;

    private static DeptRespDTO dept(long id) { var d = new DeptRespDTO(); d.setId(id); return d; }
    private static PerformanceOrgDO root(long id) { return new PerformanceOrgDO().setDeptId(id).setSalesBoundaryRoot(true); }
    private void user(long id, Long deptId) { when(users.getUser(id)).thenReturn(new AdminUserRespDTO().setId(id).setDeptId(deptId)); }
    private DeptDataPermissionRespDTO scope(long userId) {
        var scope = new DeptDataPermissionRespDTO(); scope.setDeptIds(Set.of());
        when(permissions.getDeptDataPermission(userId)).thenReturn(scope);
        return scope;
    }

    @BeforeEach void organization() {
        for (long id : new long[]{1, 10, 11, 20, 30}) when(departments.getDept(id)).thenReturn(dept(id));
        when(departments.getParentDeptList(1L)).thenReturn(List.of());
        when(departments.getParentDeptList(10L)).thenReturn(List.of(dept(1)));
        when(departments.getParentDeptList(11L)).thenReturn(List.of(dept(10), dept(1)));
        when(departments.getParentDeptList(20L)).thenReturn(List.of(dept(1)));
        when(departments.getParentDeptList(30L)).thenReturn(List.of(dept(1)));
        when(departments.getChildDeptList(10L)).thenReturn(List.of(dept(11)));
        when(departments.getChildDeptList(20L)).thenReturn(List.of());
        when(organizations.selectList()).thenReturn(List.of(root(10), root(20)));
    }

    @Test void rootResolutionAndNestedRootsFailClosed() {
        assertEquals(10L, service.rootForDepartment(11L));
        assertNull(service.findRootForDepartment(30L));
        assertThrows(ServiceException.class, () -> service.rootForDepartment(30L));
        when(organizations.selectList()).thenReturn(List.of(root(10), root(1)));
        assertThrows(ServiceException.class, () -> service.findRootForDepartment(11L));
    }

    @Test void nonSalesUsersKeepOriginalBehavior() {
        user(5, 30L); user(6, null);
        assertFalse(service.isSalesActor(5L));
        assertFalse(service.isSalesActor(6L));
        user(2, 20L);
        assertTrue(service.canAccessUser(5L, 2L));
        assertTrue(service.canTransferTo(6L, 2L));
        assertTrue(service.canUseTenantWide(5L));
        when(organizations.selectList()).thenReturn(List.of());
        assertFalse(service.isSalesActor(5L), "unconfigured roots must not break non-sales users");
    }

    @Test void qualifiedSalesOutsideRootsFailsClosed() {
        user(5, 30L);
        when(permissions.hasAnyPermissions(5L, "zsjos:sales:eligible")).thenReturn(true);
        assertThrows(ServiceException.class, () -> service.isSalesActor(5L));
    }

    @Test void tenantAdministratorsAreNotSalesActors() {
        user(9, 11L);
        when(permissions.hasTenantReadAllAccess(9L)).thenReturn(true);
        assertFalse(service.isSalesActor(9L));
    }

    @Test void dataAccessNeedsBoundaryAndSystemScope() {
        user(1, 11L); user(2, 20L); user(3, 10L); user(4, 30L);
        var scope = scope(1);
        assertTrue(service.canAccessUser(1L, 1L));
        assertFalse(service.canAccessUser(1L, 3L), "same root but outside System data scope");
        scope.setDeptIds(Set.of(10L, 20L));
        assertTrue(service.canAccessUser(1L, 3L));
        assertFalse(service.canAccessUser(1L, 2L), "other root without cross grant");
        assertFalse(service.canAccessUser(1L, 4L), "owner moved to a non-sales department");
        when(permissions.hasAnyPermissions(1L, "zsjos:sales:cross-department")).thenReturn(true);
        assertTrue(service.canAccessUser(1L, 2L));
    }

    @Test void transferTargetsIgnoreDataScopeButKeepBoundary() {
        user(1, 11L); user(2, 20L); user(3, 10L);
        scope(1);
        assertTrue(service.canTransferTo(1L, 3L), "SELF data scope may still hand over to a colleague");
        assertFalse(service.canTransferTo(1L, 2L));
        when(users.getUserList(anyCollection())).thenReturn(List.of(
                new AdminUserRespDTO().setId(2L).setDeptId(20L), new AdminUserRespDTO().setId(3L).setDeptId(10L)));
        assertEquals(Set.of(3L), service.filterTransferTargets(1L, List.of(2L, 3L)));
        when(permissions.hasAnyPermissions(1L, "zsjos:sales:cross-department")).thenReturn(true);
        assertTrue(service.canTransferTo(1L, 2L));
    }

    @Test void accessibleDepartmentsAreOwnRootIntersectedWithScope() {
        user(1, 11L);
        var scope = scope(1);
        scope.setDeptIds(Set.of(11L, 20L, 30L));
        assertEquals(Set.of(11L), service.accessibleDepartmentIds(1L));
        when(users.getUserListByDeptIds(Set.of(11L))).thenReturn(List.of(new AdminUserRespDTO().setId(7L).setDeptId(11L)));
        assertEquals(Set.of(1L, 7L), service.accessibleSalesUserIds(1L));
    }

    @Test void fullTenantManagementNeedsBothCrossPermissionAndSystemAll() {
        user(7, 10L);
        var scope = scope(7);
        assertFalse(service.canUseTenantWide(7L));
        when(permissions.hasAnyPermissions(7L, "zsjos:sales:cross-department")).thenReturn(true);
        assertFalse(service.canUseTenantWide(7L));
        scope.setAll(true);
        assertTrue(service.canUseTenantWide(7L));
    }

    @Test void revokedSalesQualificationDoesNotRemoveDepartmentBoundary() {
        user(7, 10L);
        assertTrue(service.isSalesActor(7L));
        verify(permissions, never()).hasAnyPermissions(7L, "zsjos:sales:eligible");
    }

    /** Row data permission on system_dept/system_users would hide parent roots and colleagues (found in dev e2e). */
    @Test void organizationLookupsIgnoreCallerRowDataPermission() {
        var annotation = SalesDepartmentBoundaryService.class.getAnnotation(
                cn.iocoder.yudao.framework.datapermission.core.annotation.DataPermission.class);
        assertNotNull(annotation);
        assertFalse(annotation.enable());
    }
}
