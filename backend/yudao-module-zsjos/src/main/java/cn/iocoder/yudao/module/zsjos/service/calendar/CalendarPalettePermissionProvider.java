package cn.iocoder.yudao.module.zsjos.service.calendar;
import cn.iocoder.yudao.module.zsjos.framework.permission.ZsjosObjectPermissionProvider;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;
import java.util.Objects;
import static cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId;
import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
@Component
public class CalendarPalettePermissionProvider implements ZsjosObjectPermissionProvider {
    @Resource private PermissionApi permissions;
    @Override public String getBizType() { return "calendar-palette"; }
    public static String permission(String type, boolean write) {
        if (!"PERSONAL".equals(type) && !"COURSE".equals(type)) throw exception(CalendarColors.DENIED);
        return "zsjos:" + ("PERSONAL".equals(type) ? "personal" : "course") + "-calendar:" + (write ? "color-manage" : "query");
    }
    @Override public boolean hasPermission(Long id, String action, Long user) {
        if (user == null || !Objects.equals(id, user) || !Objects.equals(user, getLoginUserId())) return false;
        // The aspect verifies owner identity; the service additionally checks the calendar-specific permission.
        if ("read".equals(action) || "write".equals(action)) return true;
        String[] parts = action == null ? new String[0] : action.split(":");
        if (parts.length != 2 || !("read".equals(parts[1]) || "write".equals(parts[1]))) return false;
        return permissions.hasAnyPermissions(user, permission(parts[0], "write".equals(parts[1])));
    }
    @Override public void check(Long id, String action, Long user) {
        if (!hasPermission(id, action, user)) throw exception(CalendarColors.DENIED);
    }
}
