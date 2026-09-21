package cn.iocoder.yudao.module.system.controller.admin.user;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.datapermission.core.aop.DataPermissionContextHolder;
import cn.iocoder.yudao.framework.security.core.LoginUser;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.system.dal.dataobject.user.AdminUserDO;
import cn.iocoder.yudao.module.system.service.dept.DeptService;
import cn.iocoder.yudao.module.system.service.permission.PermissionService;
import cn.iocoder.yudao.module.system.service.user.AdminUserService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.core.context.SecurityContextHolder;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserReadScopeControllerTest {
    @Mock AdminUserService userService;
    @Mock DeptService deptService;
    @Mock PermissionService permissionService;
    @InjectMocks UserController controller;

    @BeforeEach void setup() {
        TenantContextHolder.setTenantId(1L);
        SecurityFrameworkUtils.setLoginUser(new LoginUser().setId(9L).setUserType(2), new MockHttpServletRequest());
    }
    @AfterEach void clear() { TenantContextHolder.clear(); SecurityContextHolder.clearContext(); DataPermissionContextHolder.clear(); }

    @Test void ordinaryUserCannotRequestDisabledCandidates() {
        assertThrows(ServiceException.class, () -> controller.getSimpleUserList(null, true));
        verifyNoInteractions(userService, deptService);
    }
    @Test void administratorCandidateReadIgnoresDepartmentOnlyAndRestoresContext() {
        when(permissionService.hasTenantReadAllAccess(9L)).thenReturn(true);
        when(userService.getUserListByStatus(0, null)).thenAnswer(call -> {
            assertFalse(DataPermissionContextHolder.get().enable());
            assertEquals(1L, TenantContextHolder.getTenantId());
            return List.of(new AdminUserDO().setId(10L).setNickname("Enabled"));
        });
        when(userService.getUserListByStatus(1, null)).thenReturn(List.of(new AdminUserDO().setId(20L).setNickname("Disabled")));
        assertEquals(2, controller.getSimpleUserList(null, true).getData().size());
        assertNull(DataPermissionContextHolder.get());
        assertEquals(1L, TenantContextHolder.getTenantId());
    }
}
