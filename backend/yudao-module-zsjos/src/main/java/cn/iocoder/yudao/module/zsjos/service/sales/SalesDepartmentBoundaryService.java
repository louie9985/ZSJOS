package cn.iocoder.yudao.module.zsjos.service.sales;

import cn.iocoder.yudao.framework.common.biz.system.permission.dto.DeptDataPermissionRespDTO;
import cn.iocoder.yudao.module.system.api.dept.DeptApi;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import cn.iocoder.yudao.module.system.api.user.dto.AdminUserRespDTO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.performance.PerformanceOrgDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.performance.PerformanceOrgMapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;

import java.util.*;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants.SALES_DEPARTMENT_BOUNDARY_INVALID;

/**
 * Resolves explicitly configured sales boundaries; never infers them from names or IDs.
 * <p>
 * Boundaries only constrain sales actors (members of a configured root branch). Non-sales users keep their original
 * System permission and data-scope behavior. Misconfiguration fails closed only for users holding the sales
 * qualification, so a sales member can never fall back to unrestricted non-sales behavior.
 * Data follows each owner's current department ("跟人走").
 * <p>
 * Organization lookups ignore the caller's row-level data permission: the boundary must resolve parent departments
 * and target users outside the caller's own scope, and System data scope is applied explicitly below instead.
 */
@Service
@cn.iocoder.yudao.framework.datapermission.core.annotation.DataPermission(enable = false)
public class SalesDepartmentBoundaryService {
    static final String SALES_ELIGIBLE = "zsjos:sales:eligible";
    static final String CROSS_DEPARTMENT = "zsjos:sales:cross-department";
    private static final String CACHE_PREFIX = SalesDepartmentBoundaryService.class.getName() + ".";

    @Resource private PerformanceOrgMapper organizations;
    @Resource private DeptApi departments;
    @Resource private PermissionApi permissions;
    @Resource private AdminUserApi users;

    /** Configured root for a department; throws when the department is not inside exactly one root. */
    public Long rootForDepartment(Long deptId) {
        Long root = findRootForDepartment(deptId);
        if (root == null) throw exception(SALES_DEPARTMENT_BOUNDARY_INVALID);
        return root;
    }

    /** Configured root, or null when the department is outside every root; overlapping roots still fail closed. */
    public Long findRootForDepartment(Long deptId) {
        if (deptId == null) return null;
        return cached("root:" + deptId, () -> {
            if (departments.getDept(deptId) == null) return Optional.<Long>empty();
            Set<Long> ancestors = ancestors(deptId);
            List<Long> matches = roots().stream().filter(ancestors::contains).toList();
            if (matches.size() > 1) throw exception(SALES_DEPARTMENT_BOUNDARY_INVALID);
            return matches.stream().findFirst();
        }).orElse(null);
    }

    public boolean sameBoundary(Long first, Long second) {
        Long root = findRootForDepartment(first);
        return root != null && root.equals(findRootForDepartment(second));
    }

    /**
     * Whether the user is constrained by sales boundaries. Tenant-wide administrators and users outside every root
     * are not sales actors; a qualified sales user outside exactly one root is a configuration error.
     */
    public boolean isSalesActor(Long userId) {
        if (userId == null) return false;
        return cached("actor:" + userId, () -> {
            if (permissions.hasTenantReadAllAccess(userId)) return false;
            AdminUserRespDTO user = users.getUser(userId);
            if (user == null) return false;
            Long root = roots().isEmpty() ? null : findRootForDepartment(user.getDeptId());
            if (root != null) return true;
            if (permissions.hasAnyPermissions(userId, SALES_ELIGIBLE)) throw exception(SALES_DEPARTMENT_BOUNDARY_INVALID);
            return false;
        });
    }

    /** Whole-tenant management features require an explicit cross-department grant for sales actors. */
    public boolean canUseTenantWide() {
        return canUseTenantWide(cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId());
    }

    public boolean canUseTenantWide(Long userId) {
        if (userId == null) return false;
        if (!isSalesActor(userId)) return true;
        if (!hasCross(userId)) return false;
        DeptDataPermissionRespDTO scope = scope(userId);
        return scope != null && Boolean.TRUE.equals(scope.getAll());
    }

    /**
     * Data access to records owned by {@code targetId}. Non-sales actors are not narrowed here; sales actors need the
     * same boundary (or the cross-department grant) plus the System data scope covering the target's department.
     */
    public boolean canAccessUser(Long actorId, Long targetId) {
        if (actorId == null || targetId == null) return false;
        if (!isSalesActor(actorId)) return true;
        if (Objects.equals(actorId, targetId)) return true;
        AdminUserRespDTO target = users.getUser(targetId);
        if (target == null || target.getDeptId() == null) return false;
        if (!withinBoundary(actorId, target.getDeptId())) return false;
        DeptDataPermissionRespDTO scope = scope(actorId);
        return scope != null && (Boolean.TRUE.equals(scope.getAll())
                || scope.getDeptIds() != null && scope.getDeptIds().contains(target.getDeptId()));
    }

    /**
     * Whether a record may be handed to {@code targetId} (transfer/assignment candidate). Only the department boundary
     * applies; System data scope governs reading, not choosing a same-department colleague as the new owner.
     */
    public boolean canTransferTo(Long actorId, Long targetId) {
        if (actorId == null || targetId == null) return false;
        if (!isSalesActor(actorId) || Objects.equals(actorId, targetId)) return true;
        AdminUserRespDTO target = users.getUser(targetId);
        return target != null && target.getDeptId() != null && withinBoundary(actorId, target.getDeptId());
    }

    public Set<Long> filterAccessibleUsers(Long actorId, Collection<Long> targetIds) {
        if (actorId == null || targetIds == null || targetIds.isEmpty()) return Set.of();
        if (!isSalesActor(actorId)) return new HashSet<>(targetIds);
        Set<Long> allowed = accessibleDepartmentIds(actorId);
        return users.getUserList(targetIds).stream()
                .filter(user -> allowed.contains(user.getDeptId()) || Objects.equals(actorId, user.getId()))
                .map(AdminUserRespDTO::getId).collect(Collectors.toSet());
    }

    /** Transfer candidates inside the actor's boundary, independent of the actor's System data scope. */
    public Set<Long> filterTransferTargets(Long actorId, Collection<Long> targetIds) {
        if (actorId == null || targetIds == null || targetIds.isEmpty()) return Set.of();
        if (!isSalesActor(actorId)) return new HashSet<>(targetIds);
        Set<Long> allowed = boundaryDepartmentIds(actorId);
        return users.getUserList(targetIds).stream()
                .filter(user -> allowed.contains(user.getDeptId()) || Objects.equals(actorId, user.getId()))
                .map(AdminUserRespDTO::getId).collect(Collectors.toSet());
    }

    /**
     * Configured business departments the actor may read: boundary branches (own root only, unless the actor is
     * administrative, non-sales, or holds the cross-department grant) intersected with the System data scope.
     */
    public Set<Long> accessibleDepartmentIds(Long actorId) {
        if (actorId == null) return Set.of();
        return cached("depts:" + actorId, () -> {
            Set<Long> branch = boundaryDepartmentIds(actorId);
            if (permissions.hasTenantReadAllAccess(actorId)) return branch;
            DeptDataPermissionRespDTO scope = scope(actorId);
            if (scope == null) return Set.<Long>of();
            if (Boolean.TRUE.equals(scope.getAll())) return branch;
            Set<Long> result = new HashSet<>(branch);
            result.retainAll(scope.getDeptIds() == null ? Set.of() : scope.getDeptIds());
            return Collections.unmodifiableSet(result);
        });
    }

    /** Users whose owned records the actor may read; always includes the actor and disabled historical owners. */
    public Set<Long> accessibleSalesUserIds(Long actorId) {
        if (actorId == null) return Set.of();
        return cached("users:" + actorId, () -> {
            Set<Long> deptIds = accessibleDepartmentIds(actorId);
            Set<Long> result = deptIds.isEmpty() ? new HashSet<>() : users.getUserListByDeptIds(deptIds)
                    .stream().map(AdminUserRespDTO::getId).collect(Collectors.toCollection(HashSet::new));
            if (!permissions.hasTenantReadAllAccess(actorId)) result.add(actorId);
            return Collections.unmodifiableSet(result);
        });
    }

    /** Users who may receive records from the actor (boundary only, independent of System data scope). */
    public Set<Long> transferableSalesUserIds(Long actorId) {
        if (actorId == null) return Set.of();
        return cached("transferable:" + actorId, () -> {
            Set<Long> deptIds = boundaryDepartmentIds(actorId);
            Set<Long> result = deptIds.isEmpty() ? new HashSet<>() : users.getUserListByDeptIds(deptIds)
                    .stream().map(AdminUserRespDTO::getId).collect(Collectors.toCollection(HashSet::new));
            result.add(actorId);
            return Collections.unmodifiableSet(result);
        });
    }

    public void validateRootChange(Long deptId, boolean enabled) {
        if (!enabled) return;
        if (deptId == null || departments.getDept(deptId) == null) throw exception(SALES_DEPARTMENT_BOUNDARY_INVALID);
        Set<Long> proposedAncestors = ancestors(deptId);
        for (PerformanceOrgDO row : organizations.selectList()) {
            Long existing = row.getDeptId();
            if (!Boolean.TRUE.equals(row.getSalesBoundaryRoot()) || Objects.equals(existing, deptId)) continue;
            if (existing == null || departments.getDept(existing) == null
                    || proposedAncestors.contains(existing) || ancestors(existing).contains(deptId))
                throw exception(SALES_DEPARTMENT_BOUNDARY_INVALID);
        }
    }

    private boolean withinBoundary(Long actorId, Long targetDeptId) {
        Long targetRoot = findRootForDepartment(targetDeptId);
        if (targetRoot == null) return hasCross(actorId);
        AdminUserRespDTO actor = users.getUser(actorId);
        Long actorRoot = actor == null ? null : findRootForDepartment(actor.getDeptId());
        return targetRoot.equals(actorRoot) || hasCross(actorId);
    }

    /** Departments inside the boundary branches available to the actor, before System data scope. */
    private Set<Long> boundaryDepartmentIds(Long actorId) {
        return cached("branch:" + actorId, () -> {
            List<Long> roots = roots();
            Long ownRoot = null;
            if (isSalesActor(actorId) && !hasCross(actorId)) {
                AdminUserRespDTO actor = users.getUser(actorId);
                ownRoot = actor == null ? null : findRootForDepartment(actor.getDeptId());
                if (ownRoot == null) return Set.<Long>of();
            }
            Set<Long> result = new HashSet<>();
            for (Long root : roots) {
                if (ownRoot != null && !ownRoot.equals(root)) continue;
                result.add(root);
                departments.getChildDeptList(root).forEach(dept -> result.add(dept.getId()));
            }
            return Collections.unmodifiableSet(result);
        });
    }

    /** Configured roots; nested roots are rejected once here instead of per department. */
    private List<Long> roots() {
        return cached("roots", () -> {
            List<Long> roots = organizations.selectList().stream()
                    .filter(row -> Boolean.TRUE.equals(row.getSalesBoundaryRoot()))
                    .map(PerformanceOrgDO::getDeptId).filter(Objects::nonNull).distinct().toList();
            for (Long root : roots) {
                Set<Long> parents = new HashSet<>();
                departments.getParentDeptList(root).forEach(dept -> parents.add(dept.getId()));
                if (roots.stream().anyMatch(parents::contains)) throw exception(SALES_DEPARTMENT_BOUNDARY_INVALID);
            }
            return roots;
        });
    }

    private boolean hasCross(Long userId) {
        return cached("cross:" + userId, () -> permissions.hasAnyPermissions(userId, CROSS_DEPARTMENT));
    }

    private DeptDataPermissionRespDTO scope(Long userId) {
        return cached("scope:" + userId, () -> Optional.ofNullable(permissions.getDeptDataPermission(userId))).orElse(null);
    }

    private Set<Long> ancestors(Long deptId) {
        Set<Long> ids = new HashSet<>();
        ids.add(deptId);
        departments.getParentDeptList(deptId).forEach(dept -> ids.add(dept.getId()));
        return ids;
    }

    /** Request-scoped memoization: list and batch paths resolve the same boundary many times per request. */
    @SuppressWarnings("unchecked")
    private <T> T cached(String key, Supplier<T> loader) {
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        if (attributes == null) return loader.get();
        String name = CACHE_PREFIX + key;
        Object value = attributes.getAttribute(name, RequestAttributes.SCOPE_REQUEST);
        if (value != null) return (T) value;
        T loaded = loader.get();
        if (loaded != null) attributes.setAttribute(name, loaded, RequestAttributes.SCOPE_REQUEST);
        return loaded;
    }
}
