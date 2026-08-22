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

@Component
public class StudentServiceObjectPermissionProvider implements ZsjosObjectPermissionProvider {
    private static final Set<String> OWNER_ACTIONS = Set.of("read", "accept", "contact", "assign", "update-basic-info");

    @Resource private ServiceRelationMapper relationMapper;
    @Resource private PermissionApi permissionApi;

    @Override public String getBizType() { return "student-service"; }

    @Override
    public boolean hasPermission(Long bizId, String action, Long userId) {
        ServiceRelationDO relation = relationMapper.selectById(bizId);
        if (relation == null || !"active".equals(relation.getStatus())) return false;
        if (Objects.equals(relation.getOwnerUserId(), userId)) return OWNER_ACTIONS.contains(action);
        if ("read".equals(action) && "accepted".equals(relation.getAcceptanceStatus())) {
            return Objects.equals(relation.getContentDirectorUserId(), userId)
                    || Objects.equals(relation.getCareerPlannerUserId(), userId);
        }
        return "assign".equals(action) && permissionApi.hasAnyPermissions(userId, PERMISSION_COLLABORATOR_CORRECT);
    }

    @Override
    public void check(Long bizId, String action, Long userId) {
        if (!hasPermission(bizId, action, userId)) throw exception(STUDENT_PERMISSION_DENIED);
    }
}
