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
    @Resource private cn.iocoder.yudao.module.zsjos.service.sales.SalesDepartmentBoundaryService salesBoundary;
    @Resource private AdminUserApi adminUserApi;
    @Resource private cn.iocoder.yudao.module.zsjos.dal.mysql.deliveryclass.DeliveryClassMapper classMapper;

    /** Only the configured manager capability exposes the tenant's actual pending class. */
    public Long readablePendingClassId(Long userId) {
        if (!permissionApi.hasTenantReadAllAccess(userId) && salesBoundary.isSalesActor(userId)) return null;
        if (!permissionApi.hasAnyPermissions(userId, DeliveryClassService.PERMISSION_QUERY_MANAGED)) return null;
        var pending = classMapper.selectPending();
        return pending == null ? null : pending.getId();
    }

    /**
     * Homeroom teachers whose classes a sales actor may see, or null for non-sales actors. Sales classes follow the
     * head teacher's current department, so the class's stored dept snapshot is not used for sales visibility.
     */
    public java.util.Optional<Set<Long>> salesHomerooms(Long userId) {
        return salesBoundary.isSalesActor(userId) ? java.util.Optional.of(salesBoundary.accessibleSalesUserIds(userId))
                : java.util.Optional.empty();
    }

    /** Head teachers a sales actor may hand students to (class pickers), or null for non-sales actors. */
    public java.util.Optional<Set<Long>> salesTransferHomerooms(Long userId) {
        return salesBoundary.isSalesActor(userId) ? java.util.Optional.of(salesBoundary.transferableSalesUserIds(userId))
                : java.util.Optional.empty();
    }

    /** System SELF is an identity scope, never a grant to the user's department. */
    public Scope resolveRead(Long userId) {
        if (!permissionApi.hasTenantReadAllAccess(userId) && salesBoundary.isSalesActor(userId))
            return new Scope(false, salesBoundary.accessibleDepartmentIds(userId));
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
        return resolve(userId, () -> adminUserApi.getUser(userId));
    }

    Scope resolve(Long userId, AdminUserRespDTO currentUser) {
        return resolve(userId, () -> currentUser);
    }

    private Scope resolve(Long userId, java.util.function.Supplier<AdminUserRespDTO> currentUser) {
        boolean sales = salesBoundary.isSalesActor(userId);
        DeptDataPermissionRespDTO permission = permissionApi.getDeptDataPermission(userId);
        if (sales) {
            // Same command semantics as non-sales (SELF still covers the own department), limited to the boundary.
            Set<Long> deptIds = new LinkedHashSet<>(salesBoundary.accessibleDepartmentIds(userId));
            AdminUserRespDTO current = permission != null && Boolean.TRUE.equals(permission.getSelf()) ? currentUser.get() : null;
            if (current != null && current.getDeptId() != null && salesBoundary.findRootForDepartment(current.getDeptId()) != null)
                deptIds.add(current.getDeptId());
            return new Scope(false, deptIds);
        }
        if (permission != null && Boolean.TRUE.equals(permission.getAll())) return new Scope(true, Set.of());
        Set<Long> deptIds = new LinkedHashSet<>();
        if (permission != null && permission.getDeptIds() != null) deptIds.addAll(permission.getDeptIds());
        if (permission != null && Boolean.TRUE.equals(permission.getSelf())) {
            AdminUserRespDTO current = currentUser.get();
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
