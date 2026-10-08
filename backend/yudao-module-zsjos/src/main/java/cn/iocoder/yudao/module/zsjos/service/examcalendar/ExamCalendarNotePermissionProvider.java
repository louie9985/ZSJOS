package cn.iocoder.yudao.module.zsjos.service.examcalendar;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import cn.iocoder.yudao.module.zsjos.framework.permission.ZsjosObjectPermissionProvider;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;
import java.util.Objects;
import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.zsjos.enums.ExamNoteErrorCodes.DENIED;

@Component
public class ExamCalendarNotePermissionProvider implements ZsjosObjectPermissionProvider {
    public static final String BIZ_TYPE = "exam-calendar-note";
    @Resource private PermissionApi permissions;
    @Override public String getBizType() { return BIZ_TYPE; }
    // This singleton's business identity is its tenant, including before the first save.
    @Override public boolean hasPermission(Long tenantId, String action, Long userId) {
        if (userId == null || tenantId == null || !Objects.equals(tenantId, TenantContextHolder.getTenantId())) return false;
        return switch (action) {
            case "read" -> permissions.hasAnyPermissions(userId, "zsjos:exam-calendar:query");
            case "write" -> permissions.hasAnyPermissions(userId, "zsjos:exam-calendar:manage");
            default -> false;
        };
    }
    @Override public void check(Long id, String action, Long userId) {
        if (!hasPermission(id, action, userId)) throw exception(DENIED);
    }
}
