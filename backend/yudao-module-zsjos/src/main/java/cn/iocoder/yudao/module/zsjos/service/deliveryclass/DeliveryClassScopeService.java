package cn.iocoder.yudao.module.zsjos.service.deliveryclass;

import cn.iocoder.yudao.framework.common.biz.system.permission.dto.DeptDataPermissionRespDTO;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import cn.iocoder.yudao.module.system.api.user.dto.AdminUserRespDTO;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

@Component
public class DeliveryClassScopeService {
    @Resource private PermissionApi permissionApi;
    @Resource private AdminUserApi adminUserApi;
    @Resource private cn.iocoder.yudao.module.zsjos.dal.mysql.deliveryclass.DeliveryClassMapper classMapper;

    /** Only the configured manager capability exposes the tenant's actual pending class. */
    public Long readablePendingClassId(Long userId) {
        if (!permissionApi.hasAnyPermissions(userId, DeliveryClassService.PERMISSION_QUERY_MANAGED)) return null;
        var pending = classMapper.selectPending();
        return pending == null ? null : pending.getId();
    }

    /** System SELF is an identity scope, never a grant to the user's department. */
    public Scope resolveRead(Long userId) {
        DeptDataPermissionRespDTO permission = permissionApi.getDeptDataPermission(userId);
        if (permission != null && Boolean.TRUE.equals(permission.getAll())) return new Scope(true, Set.of());
        return new Scope(false, permission == null ? Set.of() : permission.getDeptIds());
    }

    public boolean containsRead(Long userId, Long deptId) {
        if (deptId == null) return false;
        Scope scope = resolveRead(userId);
        return scope.allDepartments() || scope.deptIds().contains(deptId);
    }

    /** Retained for existing command boundaries; changing reads must not change transfer eligibility. */
    public Scope resolve(Long userId) {
        DeptDataPermissionRespDTO permission = permissionApi.getDeptDataPermission(userId);
        if (permission != null && Boolean.TRUE.equals(permission.getAll())) return new Scope(true, Set.of());
        Set<Long> deptIds = new LinkedHashSet<>();
        if (permission != null && permission.getDeptIds() != null) deptIds.addAll(permission.getDeptIds());
        if (permission != null && Boolean.TRUE.equals(permission.getSelf())) {
            AdminUserRespDTO current = adminUserApi.getUser(userId);
            if (current != null && current.getDeptId() != null) deptIds.add(current.getDeptId());
        }
        return new Scope(false, deptIds);
    }

    public boolean contains(Long userId, Long deptId) {
        if (deptId == null) return false;
        Scope scope = resolve(userId);
        return scope.allDepartments() || scope.deptIds().contains(deptId);
    }

    public record Scope(boolean allDepartments, Set<Long> deptIds) {
        public Scope {
            deptIds = deptIds == null ? Set.of() : Set.copyOf(deptIds.stream().filter(Objects::nonNull).toList());
        }
    }
}
