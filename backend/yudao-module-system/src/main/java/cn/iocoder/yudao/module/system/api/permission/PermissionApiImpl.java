package cn.iocoder.yudao.module.system.api.permission;

import cn.iocoder.yudao.framework.common.biz.system.permission.dto.DeptDataPermissionRespDTO;
import cn.iocoder.yudao.module.system.service.permission.PermissionService;
import org.springframework.stereotype.Service;

import jakarta.annotation.Resource;
import java.util.Collection;
import java.util.Set;

/**
 * 权限 API 实现类
 *
 * @author 芋道源码
 */
@Service
public class PermissionApiImpl implements PermissionApi {

    @Resource
    private PermissionService permissionService;

    @Resource
    private cn.iocoder.yudao.module.system.service.permission.MenuService menuService;

    @Override
    public boolean isPermissionDefined(String permission) {
        if (permission == null || permission.isBlank() || permission.length() > 100 || "*:*:*".equals(permission)) return false;
        return getDefinedPermissions().stream().anyMatch(item -> permission.equals(item.permission()));
    }

    @Override
    public java.util.List<PermissionDefinition> getDefinedPermissions() {
        var request = new cn.iocoder.yudao.module.system.controller.admin.permission.vo.menu.MenuListReqVO();
        // Keep disabled ancestors in the candidate tree so the standard hierarchy filter can reject descendants.
        var unique = new java.util.LinkedHashMap<String, PermissionDefinition>();
        menuService.filterDisableMenus(menuService.getMenuListByTenant(request)).stream()
                .filter(menu -> menu.getPermission()!=null && !menu.getPermission().isBlank() && !"*:*:*".equals(menu.getPermission()))
                .forEach(menu -> unique.putIfAbsent(menu.getPermission(),new PermissionDefinition(menu.getPermission(),menu.getName())));
        return java.util.List.copyOf(unique.values());
    }

    @Override
    public boolean hasTenantReadAllAccess(Long userId) {
        return permissionService.hasTenantReadAllAccess(userId);
    }

    @Override
    public Set<Long> getEnabledRoleIdsByUserId(Long userId) {
        return permissionService.getEnabledUserRoleIdListByUserId(userId);
    }

    @Override
    public Set<Long> getUserRoleIdListByRoleIds(Collection<Long> roleIds) {
        return permissionService.getUserRoleIdListByRoleId(roleIds);
    }

    @Override
    public Set<Long> getEnabledUserIdsByPermission(String permission) {
        return permissionService.getEnabledUserIdsByPermission(permission);
    }

    @Override
    public void addUserRole(Long userId, Long roleId) {
        Set<Long> roleIds = permissionService.getUserRoleIdListByUserId(userId);
        roleIds.add(roleId);
        permissionService.assignUserRole(userId, roleIds);
    }

    @Override
    public void removeUserRole(Long userId, Long roleId) {
        Set<Long> roleIds = permissionService.getUserRoleIdListByUserId(userId);
        roleIds.remove(roleId);
        permissionService.assignUserRole(userId, roleIds);
    }

    @Override
    public boolean hasAnyPermissions(Long userId, String... permissions) {
        return permissionService.hasAnyPermissions(userId, permissions);
    }

    @Override
    public boolean hasAnyRoles(Long userId, String... roles) {
        return permissionService.hasAnyRoles(userId, roles);
    }

    @Override
    public DeptDataPermissionRespDTO getDeptDataPermission(Long userId) {
        return permissionService.getDeptDataPermission(userId);
    }

}
