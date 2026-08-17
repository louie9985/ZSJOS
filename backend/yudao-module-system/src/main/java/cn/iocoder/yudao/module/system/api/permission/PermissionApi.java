package cn.iocoder.yudao.module.system.api.permission;

import cn.iocoder.yudao.framework.common.biz.system.permission.PermissionCommonApi;

import java.util.Collection;
import java.util.Set;

/**
 * 权限 API 接口
 *
 * @author 芋道源码
 */
public interface PermissionApi extends PermissionCommonApi {

    /**
     * 获得拥有多个角色的用户编号集合
     *
     * @param roleIds 角色编号集合
     * @return 用户编号集合
     */
    Set<Long> getUserRoleIdListByRoleIds(Collection<Long> roleIds);

    /** Returns enabled users whose enabled roles grant the specified menu permission. */
    Set<Long> getEnabledUserIdsByPermission(String permission);

    /** Adds one role while preserving the user's existing role assignments. */
    void addUserRole(Long userId, Long roleId);

    /** Removes one role while preserving the user's other role assignments. */
    void removeUserRole(Long userId, Long roleId);

}
