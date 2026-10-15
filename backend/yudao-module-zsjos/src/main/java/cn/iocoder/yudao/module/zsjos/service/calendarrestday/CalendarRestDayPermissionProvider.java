package cn.iocoder.yudao.module.zsjos.service.calendarrestday;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import cn.iocoder.yudao.module.zsjos.framework.permission.ZsjosObjectPermissionProvider;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;
import java.util.Objects;
import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.zsjos.enums.CalendarRestDayErrorCodes.DENIED;

@Component
public class CalendarRestDayPermissionProvider implements ZsjosObjectPermissionProvider {
    public static final String READ = "zsjos:business-task:query";
    public static final String MANAGE = "zsjos:calendar-rest-day:manage";
    @Resource private PermissionApi permissions;
    @Override public String getBizType() { return "calendar-rest-day"; }
    // Dates are shared tenant configuration; their object scope is the current tenant, not the author.
    @Override public boolean hasPermission(Long tenantId, String action, Long userId) {
        if (userId == null || tenantId == null || !Objects.equals(tenantId, TenantContextHolder.getTenantId())) return false;
        return switch (action) {
            case "read" -> permissions.hasAnyPermissions(userId, READ);
            case "write" -> permissions.hasAnyPermissions(userId, MANAGE);
            default -> false;
        };
    }
    @Override public void check(Long id, String action, Long userId) {
        if (!hasPermission(id, action, userId)) throw exception(DENIED);
    }
}
