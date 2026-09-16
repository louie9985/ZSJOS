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

    @Test // 岗位花名册是跨部门查询：指定销售、审批人等场景下不能按调用方数据范围裁剪
    void getUserListByPostIdsIsACompleteCrossDepartmentRosterQuery() throws Exception {
        Collection<Long> postIds = List.of(12L);
        when(userService.getUserListByPostIds(postIds)).thenReturn(List.of(
                new AdminUserDO().setId(3L).setNickname("销售甲").setDeptId(21L).setStatus(0),
                new AdminUserDO().setId(4L).setNickname("销售乙").setDeptId(22L).setStatus(0)));

        var users = api.getUserListByPostIds(postIds);

        assertEquals(List.of(3L, 4L), users.stream().map(value -> value.getId()).toList());
        DataPermission annotation = AdminUserApiImpl.class
                .getMethod("getUserListByPostIds", Collection.class).getAnnotation(DataPermission.class);
        assertNotNull(annotation);
        assertFalse(annotation.enable());
    }

    @Test // 启用花名册是跨模块候选人来源：强制表单全量接收人、新媒体提供方等不能被调用方数据范围裁剪
    void getUserListByStatusIsACompleteCrossModuleRosterQuery() throws Exception {
        when(userService.getUserListByStatus(0)).thenReturn(List.of(
                new AdminUserDO().setId(5L).setNickname("运营甲").setDeptId(31L).setStatus(0),
                new AdminUserDO().setId(6L).setNickname("运营乙").setDeptId(32L).setStatus(0)));

        var users = api.getUserListByStatus(0);

        assertEquals(List.of(5L, 6L), users.stream().map(value -> value.getId()).toList());
        DataPermission annotation = AdminUserApiImpl.class
                .getMethod("getUserListByStatus", Integer.class).getAnnotation(DataPermission.class);
        assertNotNull(annotation);
        assertFalse(annotation.enable());
    }
}
