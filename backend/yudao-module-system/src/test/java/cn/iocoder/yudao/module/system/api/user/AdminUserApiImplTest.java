package cn.iocoder.yudao.module.system.api.user;

import cn.iocoder.yudao.framework.datapermission.core.annotation.DataPermission;
import cn.iocoder.yudao.module.system.dal.dataobject.user.AdminUserDO;
import cn.iocoder.yudao.module.system.service.dept.DeptService;
import cn.iocoder.yudao.module.system.service.user.AdminUserService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collection;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminUserApiImplTest {

    @InjectMocks
    private AdminUserApiImpl api;
    @Mock
    private AdminUserService userService;
    @Mock
    private DeptService deptService;

    @Test
    void getUserListByDeptIdsIsACompleteCrossModuleRosterQuery() throws Exception {
        Collection<Long> departmentIds = List.of(10L, 11L);
        when(userService.getUserListByDeptIds(departmentIds)).thenReturn(List.of(
                new AdminUserDO().setId(1L).setNickname("主管").setDeptId(10L).setStatus(0),
                new AdminUserDO().setId(2L).setNickname("运营").setDeptId(11L).setStatus(0)));

        var users = api.getUserListByDeptIds(departmentIds);

        assertEquals(List.of(1L, 2L), users.stream().map(value -> value.getId()).toList());
        DataPermission annotation = AdminUserApiImpl.class
                .getMethod("getUserListByDeptIds", Collection.class).getAnnotation(DataPermission.class);
        assertNotNull(annotation);
        assertFalse(annotation.enable());
    }
}
