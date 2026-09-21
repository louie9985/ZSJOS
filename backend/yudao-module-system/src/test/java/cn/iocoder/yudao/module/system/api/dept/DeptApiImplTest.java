package cn.iocoder.yudao.module.system.api.dept;

import cn.iocoder.yudao.framework.datapermission.core.annotation.DataPermission;
import cn.iocoder.yudao.framework.datapermission.core.aop.DataPermissionAnnotationAdvisor;
import cn.iocoder.yudao.framework.datapermission.core.aop.DataPermissionContextHolder;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.system.dal.dataobject.dept.DeptDO;
import cn.iocoder.yudao.module.system.service.dept.DeptService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.aop.framework.ProxyFactory;

import java.lang.reflect.Method;
import java.util.Collection;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DeptApiImplTest {

    @DataPermission
    private static class CallerScope {}

    @InjectMocks
    private DeptApiImpl api;
    @Mock
    private DeptService deptService;

    @Test
    void bothChildDepartmentEntrypointsBypassCallerScopeAndRestoreIt() {
        ProxyFactory factory = new ProxyFactory(api);
        factory.setProxyTargetClass(true);
        factory.addAdvisor(new DataPermissionAnnotationAdvisor());
        DeptApi proxy = (DeptApi) factory.getProxy();
        DataPermission caller = CallerScope.class.getAnnotation(DataPermission.class);
        DataPermissionContextHolder.add(caller);
        TenantContextHolder.setTenantId(1L);
        try {
            when(deptService.getChildDeptList(org.mockito.ArgumentMatchers.<Collection<Long>>argThat(ids -> ids != null && ids.size() == 1 && ids.contains(1030L)))).thenAnswer(invocation -> {
                DataPermission scope = DataPermissionContextHolder.get();
                // A SELF-scoped caller sees no cross-department children unless the API boundary is active.
                assertNotNull(scope);
                assertEquals(1L, TenantContextHolder.getRequiredTenantId());
                assertFalse(TenantContextHolder.isIgnore());
                return scope.enable() ? List.of() : List.of(new DeptDO().setId(1031L));
            });
            assertEquals(List.of(1031L), proxy.getChildDeptList(1030L).stream().map(x -> x.getId()).toList());
            assertSame(caller, DataPermissionContextHolder.get());
            assertEquals(List.of(1031L), proxy.getChildDeptList(List.of(1030L)).stream().map(x -> x.getId()).toList());
            assertSame(caller, DataPermissionContextHolder.get());
            when(deptService.getChildDeptList(List.of(1040L))).thenThrow(new IllegalStateException("lookup failed"));
            assertThrows(IllegalStateException.class, () -> proxy.getChildDeptList(1040L));
            assertSame(caller, DataPermissionContextHolder.get());
            assertEquals(1L, TenantContextHolder.getRequiredTenantId());
        } finally {
            TenantContextHolder.clear();
            DataPermissionContextHolder.clear();
        }
        assertNull(DataPermissionContextHolder.get());
    }

    @Test
    void crossModuleOrganizationLookupsIgnoreCallerDataScope() throws Exception {
        Collection<Long> ids = List.of(10L, 11L);
        when(deptService.getDept(10L)).thenReturn(new DeptDO().setId(10L).setName("运营一部"));
        when(deptService.getDeptList(ids)).thenReturn(List.of(
                new DeptDO().setId(10L).setName("运营一部"),
                new DeptDO().setId(11L).setName("运营二部")));
        when(deptService.getDeptListByLeaderUserId(20L)).thenReturn(List.of(
                new DeptDO().setId(10L).setLeaderUserId(20L)));

        assertEquals(10L, api.getDept(10L).getId());
        assertEquals(List.of(10L, 11L), api.getDeptList(ids).stream().map(value -> value.getId()).toList());
        assertEquals(List.of(10L), api.getDeptListByLeaderUserId(20L).stream()
                .map(value -> value.getId()).toList());
        assertDataPermissionDisabled(DeptApiImpl.class.getMethod("getDept", Long.class));
        assertDataPermissionDisabled(DeptApiImpl.class.getMethod("getDeptList", Collection.class));
        assertDataPermissionDisabled(DeptApiImpl.class.getMethod("getDeptListByLeaderUserId", Long.class));
    }

    private static void assertDataPermissionDisabled(Method method) {
        DataPermission annotation = method.getAnnotation(DataPermission.class);
        assertNotNull(annotation);
        assertFalse(annotation.enable());
    }
}
