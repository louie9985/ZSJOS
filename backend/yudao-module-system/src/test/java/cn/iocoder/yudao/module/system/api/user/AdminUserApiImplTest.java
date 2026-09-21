package cn.iocoder.yudao.module.system.api.user;

import cn.iocoder.yudao.framework.datapermission.core.aop.DataPermissionContextHolder;
import cn.iocoder.yudao.framework.datapermission.core.aop.DataPermissionAnnotationAdvisor;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.framework.common.exception.ServiceException;
import org.springframework.aop.framework.ProxyFactory;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertNull;
import static cn.iocoder.yudao.module.system.enums.ErrorCodeConstants.USER_NOT_EXISTS;
import static cn.iocoder.yudao.module.system.enums.ErrorCodeConstants.USER_IS_DISABLE;
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

@DataPermission
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
    @Test
    void singleAndBatchValidationIgnoreCallerScopeAndRestoreContext() {
        var proxy = validationProxy();
        var outer = AdminUserApiImplTest.class.getAnnotation(DataPermission.class);
        DataPermissionContextHolder.add(outer);
        TenantContextHolder.setTenantId(1L);
        try {
            doAnswer(invocation -> {
                assertFalse(DataPermissionContextHolder.get().enable());
                assertEquals(1L, TenantContextHolder.getRequiredTenantId());
                assertFalse(TenantContextHolder.isIgnore());
                return null;
            }).when(userService).validateUserList(anyCollection());
            proxy.validateUser(47L);
            assertSame(outer,
                    DataPermissionContextHolder.get());
            proxy.validateUserList(List.of(47L, 48L));
            assertSame(outer,
                    DataPermissionContextHolder.get());
            verify(userService).validateUserList(java.util.Collections.singleton(47L));
            verify(userService).validateUserList(List.of(47L, 48L));
        } finally {
            DataPermissionContextHolder.clear();
            TenantContextHolder.clear();
        }
    }

    @Test
    void invalidUsersStillFailAndPermissionContextIsRestored() {
        var proxy = validationProxy();
        for (var code : List.of(
                USER_NOT_EXISTS,
                USER_IS_DISABLE)) {
            var failure = new ServiceException(code);
            doThrow(failure).when(userService)
                    .validateUserList(anyCollection());
            assertSame(failure,
                    assertThrows(
                            ServiceException.class,
                            () -> proxy.validateUser(47L)));
            assertSame(failure,
                    assertThrows(
                            ServiceException.class,
                            () -> proxy.validateUserList(List.of(47L))));
            assertNull(
                    DataPermissionContextHolder.get());
        }
    }

    private AdminUserApi validationProxy() {
        var factory = new ProxyFactory(api);
        factory.setProxyTargetClass(true);
        factory.addAdvisor(new DataPermissionAnnotationAdvisor());
        return (AdminUserApi) factory.getProxy();
    }

}
