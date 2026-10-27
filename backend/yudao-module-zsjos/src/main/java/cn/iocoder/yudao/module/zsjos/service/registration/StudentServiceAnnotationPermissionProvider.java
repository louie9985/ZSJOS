package cn.iocoder.yudao.module.zsjos.service.registration;

import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import cn.iocoder.yudao.module.zsjos.dal.mysql.registration.ServiceRelationMapper;
import cn.iocoder.yudao.module.zsjos.framework.permission.ZsjosObjectPermissionProvider;
import cn.iocoder.yudao.module.zsjos.service.deliveryclass.DeliveryClassScopeService;
import cn.iocoder.yudao.module.zsjos.service.deliveryclass.DeliveryClassService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;
import java.util.Objects;
import java.util.Set;
import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants.STUDENT_PERMISSION_DENIED;

@Component
public class StudentServiceAnnotationPermissionProvider implements ZsjosObjectPermissionProvider {
    @Resource private ServiceRelationMapper relations;
    @Resource private StudentServiceObjectPermissionProvider servicePermissions;
    @Resource private PermissionApi permissions;
    @Resource private AdminUserApi users;
    @Resource private DeliveryClassScopeService scope;

    @Override public String getBizType() { return "student-service-annotation"; }
    @Override public boolean hasPermission(Long relationId, String action, Long userId) {
        if (userId == null) return false;
        var relation = relations.selectById(relationId);
        if (relation == null || !StudentDeliveryParticipation.includes(relation)
                || !servicePermissions.hasPermission(relationId, "read", userId)) return false;
        if ("read".equals(action)) return permissions.hasAnyPermissions(userId, StudentServiceAnnotationConstants.READ);
        if (!"update".equals(action) || TeachingServiceLifecycle.closed(relation)
                || !Set.of("active", "paused", "completed").contains(relation.getStatus())
                || !permissions.hasAnyPermissions(userId, StudentServiceAnnotationConstants.READ)
                || !permissions.hasAnyPermissions(userId, StudentServiceAnnotationConstants.UPDATE)) return false;
        if (Objects.equals(relation.getOwnerUserId(), userId)) return true;
        // A read-all grant is not a management command grant. SELF never expands to the department.
        if (!permissions.hasAnyPermissions(userId, DeliveryClassService.PERMISSION_QUERY_MANAGED)) return false;
        var owner = relation.getOwnerUserId() == null ? null : users.getUser(relation.getOwnerUserId());
        return owner != null && scope.containsRead(userId, owner.getDeptId());
    }
    @Override public void check(Long bizId, String action, Long userId) {
        if (!hasPermission(bizId, action, userId)) throw exception(STUDENT_PERMISSION_DENIED);
    }
}
