package cn.iocoder.yudao.module.zsjos.service.studentinfo;

import cn.iocoder.yudao.framework.common.enums.UserTypeEnum;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.studentinfo.StudentInfoTaskDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.registration.RegistrationCaseMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.studentinfo.StudentInfoTaskMapper;
import cn.iocoder.yudao.module.zsjos.framework.permission.ZsjosObjectPermissionProvider;
import cn.iocoder.yudao.module.zsjos.service.registration.RegistrationCaseObjectPermissionProvider;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

import java.util.Objects;
import java.util.Set;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils.getLoginUser;
import static cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants.LEAD_PERMISSION_DENIED;

@Component
public class StudentInfoTaskPermissionProvider implements ZsjosObjectPermissionProvider {
    @Resource private StudentInfoTaskMapper tasks;
    @Resource private RegistrationCaseMapper cases;
    @Resource private RegistrationCaseObjectPermissionProvider casePermissions;
    @Resource private PermissionApi permissions;

    @Override public String getBizType() { return "student-info-task"; }

    public boolean canList(Long userId) {
        var user = getLoginUser();
        return user != null && UserTypeEnum.ADMIN.getValue().equals(user.getUserType())
                && permissions.hasAnyPermissions(userId, "zsjos:registration:query-my");
    }

    public boolean canAccess(StudentInfoTaskDO task, String action, Long userId) {
        if (task == null || !canList(userId)) return false;
        var item = cases.selectByOrderItemId(task.getOrderItemId());
        if (!casePermissions.isOwner(item, userId) || !Objects.equals(item.getOrderId(), task.getOrderId())) return false;
        return "read".equals(action) || casePermissions.isOpen(item)
                && Set.of("create", "reuse", "link-read", "sent", "regenerate", "revoke").contains(action);
    }

    @Override public boolean hasPermission(Long id, String action, Long userId) {
        return canList(userId) && canAccess(tasks.selectById(id), action, userId);
    }

    @Override public void check(Long id, String action, Long userId) {
        if (!hasPermission(id, action, userId)) throw exception(LEAD_PERMISSION_DENIED);
    }
}
