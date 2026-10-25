package cn.iocoder.yudao.module.zsjos.service.studentinfo;

import cn.iocoder.yudao.module.zsjos.framework.permission.ZsjosObjectPermissionProvider;
import cn.iocoder.yudao.module.zsjos.service.registration.StudentServiceObjectPermissionProvider;
import cn.iocoder.yudao.framework.common.enums.UserTypeEnum;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;
import java.util.Set;
import static cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils.getLoginUser;
import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants.LEAD_PERMISSION_DENIED;

@Component
public class StudentArchivePermissionProvider implements ZsjosObjectPermissionProvider {
    @Resource private StudentServiceObjectPermissionProvider services;
    @Override public String getBizType() { return "student-archive"; }
    @Override public boolean hasPermission(Long id, String action, Long userId) {
        var user = getLoginUser();
        // Maintenance is an independent configured operation, cumulative with exact-service visibility.
        return user != null && UserTypeEnum.ADMIN.getValue().equals(user.getUserType())
                && Set.of("read", "update").contains(action) && services.hasPermission(id, "update".equals(action) ? "archive-update" : "read", userId);
    }
    @Override public void check(Long id, String action, Long userId) {
        if (!hasPermission(id, action, userId)) throw exception(LEAD_PERMISSION_DENIED);
    }
}
