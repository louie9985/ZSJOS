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

    @Override
    public boolean hasPermission(Long bizId, String action, Long userId) {
        ServiceRelationDO relation = relationMapper.selectById(bizId);
        if (relation == null) return false;
        // Managed visibility applies only to reads and to this exact service, never to commands.
        if ("read".equals(action)
                && Set.of("active", "paused", "completed").contains(relation.getStatus())
                && hasManagedStudentReadPermission(userId)) {
            DeliveryClassScopeService.Scope scope = deliveryClassScopeService.resolve(userId);
            if (scope.allDepartments() || Objects.equals(relation.getOwnerUserId(), userId)) return true;
            return !scope.deptIds().isEmpty() && adminUserApi.getUserListByDeptIds(scope.deptIds()).stream()
                    .map(AdminUserRespDTO::getId).filter(Objects::nonNull)
                    .anyMatch(ownerId -> Objects.equals(ownerId, relation.getOwnerUserId()));
        }
        // Responsibility is cumulative: the service owner can also be its director.
        if (Set.of("director-precheck", "director-interview").contains(action)) {
            return "active".equals(relation.getStatus())
                    && "accepted".equals(relation.getAcceptanceStatus())
                    && Objects.equals(relation.getContentDirectorUserId(), userId);
        }
        if ("create-account".equals(action)) {
            return "active".equals(relation.getStatus())
                    && "accepted".equals(relation.getAcceptanceStatus())
                    && (Objects.equals(relation.getContentDirectorUserId(), userId)
                        || Objects.equals(relation.getOperatorUserId(), userId));
        }
        if ("direct-transfer".equals(action) && Set.of("active", "paused", "completed").contains(relation.getStatus())) {
            DeliveryClassDO source = relation.getClassId() == null ? null : deliveryClassMapper.selectById(relation.getClassId());
            return source != null && (Boolean.TRUE.equals(source.getSystemClass())
                    || deliveryClassScopeService.contains(userId, source.getDeptId()));
        }
        if (Objects.equals(relation.getOwnerUserId(), userId)) {
            if ("read".equals(action) && Set.of("active", "paused", "completed").contains(relation.getStatus())) return true;
            if ("class-transfer".equals(action)
                    && Set.of("active", "paused", "completed").contains(relation.getStatus())) return true;
            return "active".equals(relation.getStatus()) && OWNER_ACTIONS.contains(action);
        }
        if ("read".equals(action) && "accepted".equals(relation.getAcceptanceStatus())
                && Set.of("active", "paused", "completed").contains(relation.getStatus())
                && Objects.equals(relation.getOperatorUserId(), userId)) return true;
        if (!"active".equals(relation.getStatus())) return false;
        if ("read".equals(action) && "accepted".equals(relation.getAcceptanceStatus())) {
            return Objects.equals(relation.getContentDirectorUserId(), userId)
                    || Objects.equals(relation.getCareerPlannerUserId(), userId);
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
