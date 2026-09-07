package cn.iocoder.yudao.module.system.api.dept;

import cn.iocoder.yudao.framework.datapermission.core.annotation.DataPermission;
import cn.iocoder.yudao.module.system.dal.dataobject.dept.DeptDO;
import cn.iocoder.yudao.module.system.service.dept.DeptService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Method;
import java.util.Collection;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DeptApiImplTest {

    @InjectMocks
    private DeptApiImpl api;
    @Mock
    private DeptService deptService;

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
