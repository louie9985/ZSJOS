package cn.iocoder.yudao.module.zsjos.service.registration;

import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.registration.ServiceRelationDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.registration.ServiceRelationMapper;
import cn.iocoder.yudao.module.zsjos.framework.permission.ZsjosObjectPermissionProvider;
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
    @Resource private PermissionApi permissionApi;

    @Override public String getBizType() { return "student-service"; }

    @Override
    public boolean hasPermission(Long bizId, String action, Long userId) {
        ServiceRelationDO relation = relationMapper.selectById(bizId);
        if (relation == null) return false;
        if (Objects.equals(relation.getOwnerUserId(), userId)) {
            if ("read".equals(action) && Set.of("active", "paused", "completed").contains(relation.getStatus())) return true;
            return "active".equals(relation.getStatus()) && OWNER_ACTIONS.contains(action);
        }
        if (!"active".equals(relation.getStatus())) return false;
        if ("read".equals(action) && "accepted".equals(relation.getAcceptanceStatus())) {
            return Objects.equals(relation.getContentDirectorUserId(), userId)
                    || Objects.equals(relation.getCareerPlannerUserId(), userId)
                    || Objects.equals(relation.getOperatorUserId(), userId);
        }
        if ("assign".equals(action) && Objects.equals(relation.getContentDirectorUserId(), userId)
                && permissionApi.hasAnyPermissions(userId, PERMISSION_DIRECTOR_OPERATOR_ASSIGN)) return true;
        if (Set.of("director-precheck", "director-interview").contains(action)
                && "accepted".equals(relation.getAcceptanceStatus())
                && Objects.equals(relation.getContentDirectorUserId(), userId)) return true;
        return "assign".equals(action) && permissionApi.hasAnyPermissions(userId, PERMISSION_COLLABORATOR_CORRECT);
    }

    @Override
    public void check(Long bizId, String action, Long userId) {
        if (!hasPermission(bizId, action, userId)) throw exception(STUDENT_PERMISSION_DENIED);
    }
}
