package cn.iocoder.yudao.module.zsjos.service.account;

import cn.iocoder.yudao.framework.common.biz.system.permission.dto.DeptDataPermissionRespDTO;
import cn.iocoder.yudao.framework.common.enums.CommonStatusEnum;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import cn.iocoder.yudao.module.system.api.user.dto.AdminUserRespDTO;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

@Component
public class MediaAccountCalendarScopeService {
    public static final String PERMISSION_QUERY_MANAGED = "zsjos:media-calendar:query-managed";
    public static final String PERMISSION_QUERY_ALL = "zsjos:media-calendar:query-all";

    @Resource private PermissionApi permissionApi;
    @Resource private AdminUserApi adminUserApi;

    public Scope resolve(Long userId) {
        if (permissionApi.hasAnyPermissions(userId, PERMISSION_QUERY_ALL)) return new Scope(true, Set.of());
        Set<Long> userIds = new HashSet<>();
        userIds.add(userId);
        if (!permissionApi.hasAnyPermissions(userId, PERMISSION_QUERY_MANAGED)) return new Scope(false, userIds);
        DeptDataPermissionRespDTO scope = permissionApi.getDeptDataPermission(userId);
        // Generic unbounded department scope never substitutes for explicit calendar all-access.
        if (scope == null || Boolean.TRUE.equals(scope.getAll()) || scope.getDeptIds() == null
                || scope.getDeptIds().isEmpty()) return new Scope(false, userIds);
        adminUserApi.getUserListByDeptIds(scope.getDeptIds()).stream()
                .filter(Objects::nonNull)
                .filter(user -> Objects.equals(user.getStatus(), CommonStatusEnum.ENABLE.getStatus()))
                .map(AdminUserRespDTO::getId).forEach(userIds::add);
        return new Scope(false, userIds);
    }

    public record Scope(boolean all, Set<Long> userIds) {}
}
