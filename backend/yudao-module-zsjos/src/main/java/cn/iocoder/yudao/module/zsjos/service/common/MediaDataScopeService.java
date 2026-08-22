package cn.iocoder.yudao.module.zsjos.service.common;

import cn.iocoder.yudao.framework.common.biz.system.permission.dto.DeptDataPermissionRespDTO;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import cn.iocoder.yudao.module.system.api.user.dto.AdminUserRespDTO;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.Set;

@Component
public class MediaDataScopeService {
    @Resource private PermissionApi permissionApi;
    @Resource private AdminUserApi adminUserApi;

    public Scope resolve(Long userId, String queryAllPermission) {
        if (permissionApi.hasAnyPermissions(userId, queryAllPermission)) return new Scope(true, Set.of());
        DeptDataPermissionRespDTO departmentScope = permissionApi.getDeptDataPermission(userId);
        if (departmentScope != null && Boolean.TRUE.equals(departmentScope.getAll())) return new Scope(true, Set.of());
        Set<Long> userIds = new HashSet<>();
        userIds.add(userId);
        if (departmentScope != null && departmentScope.getDeptIds() != null && !departmentScope.getDeptIds().isEmpty()) {
            adminUserApi.getUserListByDeptIds(departmentScope.getDeptIds()).stream()
                    .map(AdminUserRespDTO::getId).forEach(userIds::add);
        }
        return new Scope(false, userIds);
    }

    public record Scope(boolean all, Set<Long> userIds) {}
}
