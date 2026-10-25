package cn.iocoder.yudao.module.zsjos.service.registration;

import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.registration.ServiceRelationDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.deliveryclass.DeliveryClassDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.deliveryclass.DeliveryClassMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.registration.ServiceRelationMapper;
import cn.iocoder.yudao.module.zsjos.framework.permission.ZsjosObjectPermissionProvider;
import cn.iocoder.yudao.module.zsjos.service.deliveryclass.DeliveryClassScopeService;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import cn.iocoder.yudao.module.system.api.user.dto.AdminUserRespDTO;
import cn.iocoder.yudao.module.zsjos.service.deliveryclass.DeliveryClassService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

import java.util.Objects;
import java.util.Set;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants.STUDENT_PERMISSION_DENIED;
import static cn.iocoder.yudao.module.zsjos.service.studentcontact.StudentContactConstants.PERMISSION_COLLABORATOR_CORRECT;
import static cn.iocoder.yudao.module.zsjos.service.studentcontact.StudentContactConstants.PERMISSION_DIRECTOR_OPERATOR_ASSIGN;

@Component
public class StudentServiceObjectPermissionProvider implements ZsjosObjectPermissionProvider {
    private static final Set<String> OWNER_ACTIONS = Set.of(
            "read", "accept", "contact", "assign", "update-basic-info", "delivery-stage");

    @Resource private ServiceRelationMapper relationMapper;
    @Resource private DeliveryClassMapper deliveryClassMapper;
    @Resource private DeliveryClassScopeService deliveryClassScopeService;
    @Resource private PermissionApi permissionApi;
    @Resource private AdminUserApi adminUserApi;

    private boolean hasManagedStudentReadPermission(Long userId) {
        return permissionApi.hasAnyPermissions(userId, "zsjos:delivery-class:query",
                DeliveryClassService.PERMISSION_QUERY_MANAGED);
    }

    @Override public String getBizType() { return "student-service"; }

    public java.util.List<String> transferActions(Long relationId, Long userId) {
        var relation = relationMapper.selectById(relationId);
        return relation == null ? java.util.List.of()
                : transferActions(java.util.List.of(relation), userId).getOrDefault(relationId, java.util.List.of());
    }

    public java.util.Map<Long, java.util.List<String>> transferActions(java.util.List<ServiceRelationDO> relations, Long userId) {
        if (relations.isEmpty()) return java.util.Map.of();
        boolean direct = permissionApi.hasAnyPermissions(userId, "zsjos:delivery-class:direct-transfer");
        boolean request = permissionApi.hasAnyPermissions(userId, "zsjos:class-transfer:create");
        var eligible = relations.stream().filter(row -> StudentDeliveryParticipation.includes(row)
                && !TeachingServiceLifecycle.closed(row) && Set.of("active", "paused", "completed").contains(row.getStatus())).toList();
        var scope = direct ? deliveryClassScopeService.resolve(userId) : new DeliveryClassScopeService.Scope(false, Set.of());
        var ids = eligible.stream().map(ServiceRelationDO::getClassId).filter(Objects::nonNull).distinct().toList();
        var classes = !direct || ids.isEmpty() ? java.util.Map.<Long, DeliveryClassDO>of()
                : deliveryClassMapper.selectByIds(ids).stream().collect(java.util.stream.Collectors.toMap(DeliveryClassDO::getId, row -> row));
        var result = new java.util.HashMap<Long, java.util.List<String>>();
        for (var relation : eligible) {
            var actions = new java.util.ArrayList<String>();
            var source = classes.get(relation.getClassId());
            if (direct && source != null && (cn.iocoder.yudao.module.zsjos.service.deliveryclass.DeliveryClassKind.pending(source)
                    || source.getDeptId() != null && scope != null && (scope.allDepartments() || scope.deptIds().contains(source.getDeptId()))))
                actions.add(cn.iocoder.yudao.module.zsjos.service.studentcontact.StudentContactConstants.CONTEXT_ACTION_DIRECT_TRANSFER);
            if (request && Objects.equals(relation.getOwnerUserId(), userId))
                actions.add(cn.iocoder.yudao.module.zsjos.service.studentcontact.StudentContactConstants.CONTEXT_ACTION_REQUEST_TRANSFER);
            result.put(relation.getId(), actions);
        }
        return result;
    }

    @Override
    public boolean hasPermission(Long bizId, String action, Long userId) {
        ServiceRelationDO relation = relationMapper.selectById(bizId);
        if (relation == null) return false;
        if ("close-service".equals(action)) return StudentDeliveryParticipation.includes(relation)
                && Objects.equals(relation.getOwnerUserId(), userId);
        if ("restore-service".equals(action)) {
            var owner = relation.getOwnerUserId() == null ? null : adminUserApi.getUser(relation.getOwnerUserId());
            return StudentDeliveryParticipation.includes(relation) && owner != null
                    && deliveryClassScopeService.containsRead(userId, owner.getDeptId());
        }
        if ("archive-update".equals(action)) return !TeachingServiceLifecycle.closed(relation)
                && hasPermission(bizId, "read", userId);
        if (TeachingServiceLifecycle.closed(relation)
                && Set.of("accept", "contact", "update-basic-info", "delivery-stage", "direct-transfer", "class-transfer", "update-exam-date").contains(action)) return false;
        if (!StudentDeliveryParticipation.includes(relation)
                && (OWNER_ACTIONS.contains(action) && !Set.of("read", "assign").contains(action)
                    || Set.of("direct-transfer", "class-transfer", "update-exam-date").contains(action))) return false;
        if ("read".equals(action) && permissionApi.hasTenantReadAllAccess(userId)) return true;
        if ("read".equals(action) && StudentReadScope.canReadAll(permissionApi, userId)
                && StudentDeliveryParticipation.includes(relation)
                && Set.of("active", "paused", "completed").contains(relation.getStatus())) return true;
        if ("positioning-interview-read".equals(action)) {
            if (MediaStudentReadScope.canReadAll(permissionApi, userId)) {
                return relationMapper.selectMediaReadByPersonIds(java.util.List.of(relation.getPersonId()), null)
                        .stream().anyMatch(row -> Objects.equals(row.getId(), bizId));
            }
            return "active".equals(relation.getStatus()) && MediaServiceReadiness.ready(relation)
                    && Objects.equals(relation.getContentDirectorUserId(), userId);
        }
        if ("read".equals(action) && MediaStudentReadScope.canReadAll(permissionApi, userId)
                && relationMapper.selectMediaReadByPersonIds(java.util.List.of(relation.getPersonId()), null)
                        .stream().anyMatch(row -> Objects.equals(row.getId(), bizId))) return true;
        // Managed visibility applies only to reads and to this exact service, never to commands.
        if ("read".equals(action)
                && Set.of("active", "paused", "completed").contains(relation.getStatus())
                && hasManagedStudentReadPermission(userId)) {
            DeliveryClassScopeService.Scope scope = deliveryClassScopeService.resolveRead(userId);
            if (scope.allDepartments() || Objects.equals(relation.getOwnerUserId(), userId)) return true;
            Long pendingClassId = deliveryClassScopeService.readablePendingClassId(userId);
            if (pendingClassId != null && Objects.equals(pendingClassId, relation.getClassId())) return true;
            if (!scope.deptIds().isEmpty() && adminUserApi.getUserListByDeptIds(scope.deptIds()).stream()
                    .map(AdminUserRespDTO::getId).filter(Objects::nonNull)
                    .anyMatch(ownerId -> Objects.equals(ownerId, relation.getOwnerUserId()))) return true;
        }
        // Responsibility is cumulative: the service owner can also be its director.
        if (Set.of("director-precheck", "director-interview").contains(action)) {
            return "active".equals(relation.getStatus())
                    && MediaServiceReadiness.ready(relation)
                    && Objects.equals(relation.getContentDirectorUserId(), userId);
        }
        if ("create-account".equals(action)) {
            return "active".equals(relation.getStatus())
                    && MediaServiceReadiness.ready(relation)
                    && (Objects.equals(relation.getContentDirectorUserId(), userId)
                        || Objects.equals(relation.getOperatorUserId(), userId));
        }
        if ("direct-transfer".equals(action) && Set.of("active", "paused", "completed").contains(relation.getStatus())) {
            DeliveryClassDO source = relation.getClassId() == null ? null : deliveryClassMapper.selectById(relation.getClassId());
            return source != null && (cn.iocoder.yudao.module.zsjos.service.deliveryclass.DeliveryClassKind.pending(source)
                    || deliveryClassScopeService.contains(userId, source.getDeptId()));
        }
        if (Objects.equals(relation.getOwnerUserId(), userId)) {
            if ("read".equals(action) && Set.of("active", "paused", "completed").contains(relation.getStatus())) return true;
            if ("class-transfer".equals(action)
                    && Set.of("active", "paused", "completed").contains(relation.getStatus())) return true;
            return "active".equals(relation.getStatus()) && OWNER_ACTIONS.contains(action);
        }
        if ("read".equals(action) && MediaServiceReadiness.ready(relation)
                && Set.of("active", "paused", "completed").contains(relation.getStatus())
                && Objects.equals(relation.getOperatorUserId(), userId)) return true;
        if (!"active".equals(relation.getStatus())) return false;
        if ("read".equals(action)) {
            return MediaServiceReadiness.ready(relation) && Objects.equals(relation.getContentDirectorUserId(), userId)
                    || "accepted".equals(relation.getAcceptanceStatus()) && Objects.equals(relation.getCareerPlannerUserId(), userId);
        }
        if ("assign".equals(action) && Objects.equals(relation.getContentDirectorUserId(), userId)
                && permissionApi.hasAnyPermissions(userId, PERMISSION_DIRECTOR_OPERATOR_ASSIGN)) return true;
        return "assign".equals(action) && permissionApi.hasAnyPermissions(userId, PERMISSION_COLLABORATOR_CORRECT);
    }

    @Override
    public void check(Long bizId, String action, Long userId) {
        if (!hasPermission(bizId, action, userId)) throw exception(STUDENT_PERMISSION_DENIED);
    }
}
