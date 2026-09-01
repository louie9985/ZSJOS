package cn.iocoder.yudao.module.system.service.permission;

import cn.iocoder.yudao.framework.common.enums.CommonStatusEnum;
import cn.iocoder.yudao.module.system.dal.dataobject.permission.RoleDO;
import cn.iocoder.yudao.module.system.dal.dataobject.permission.RoleMenuDO;
import cn.iocoder.yudao.module.system.dal.dataobject.permission.UserRoleDO;
import cn.iocoder.yudao.module.system.dal.dataobject.user.AdminUserDO;
import cn.iocoder.yudao.module.system.dal.mysql.permission.RoleMenuMapper;
import cn.iocoder.yudao.module.system.dal.mysql.permission.UserRoleMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Set;

import static cn.iocoder.yudao.module.system.enums.permission.RoleCodeEnum.SUPER_ADMIN;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PermissionServiceImplPermissionUsersTest {

    @InjectMocks private PermissionServiceImpl service;
    @Mock private RoleMenuMapper roleMenuMapper;
    @Mock private UserRoleMapper userRoleMapper;
    @Mock private RoleService roleService;
    @Mock private MenuService menuService;
    @Mock private cn.iocoder.yudao.module.system.service.user.AdminUserService userService;

    @Test
    void resolvesOnlyEnabledUsersFromEnabledPermissionRoles() {
        when(menuService.getMenuIdListByPermissionFromCache("zsjos:registration:query-pool"))
                .thenReturn(List.of(100L));
        when(roleMenuMapper.selectListByMenuId(100L)).thenReturn(List.of(roleMenu(5L), roleMenu(6L)));
        RoleDO superAdmin = role(1L, SUPER_ADMIN.getCode(), CommonStatusEnum.ENABLE.getStatus());
        when(roleService.getRoleListByStatus(Set.of(CommonStatusEnum.ENABLE.getStatus())))
                .thenReturn(List.of(superAdmin));
        when(roleService.getRoleList(Set.of(5L, 6L, 1L))).thenReturn(List.of(
                superAdmin, role(5L, "registration_handler", CommonStatusEnum.ENABLE.getStatus()),
                role(6L, "disabled_handler", CommonStatusEnum.DISABLE.getStatus())));
        when(userRoleMapper.selectListByRoleIds(any())).thenReturn(List.of(userRole(11L, 5L), userRole(12L, 1L)));
        when(userService.getUserList(Set.of(11L, 12L))).thenReturn(List.of(
                user(11L, CommonStatusEnum.ENABLE.getStatus()), user(12L, CommonStatusEnum.DISABLE.getStatus())));

        assertEquals(Set.of(11L), service.getEnabledUserIdsByPermission("zsjos:registration:query-pool"));
    }

    private static RoleMenuDO roleMenu(Long roleId) {
        RoleMenuDO value = new RoleMenuDO(); value.setRoleId(roleId); value.setMenuId(100L); return value;
    }

    private static UserRoleDO userRole(Long userId, Long roleId) {
        UserRoleDO value = new UserRoleDO(); value.setUserId(userId); value.setRoleId(roleId); return value;
    }

    private static RoleDO role(Long id, String code, Integer status) {
        RoleDO value = new RoleDO(); value.setId(id); value.setCode(code); value.setStatus(status); return value;
    }

    private static AdminUserDO user(Long id, Integer status) {
        AdminUserDO value = new AdminUserDO(); value.setId(id); value.setStatus(status); return value;
    }
}
