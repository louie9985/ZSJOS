package cn.iocoder.yudao.module.zsjos.service.coursecalendar;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import cn.iocoder.yudao.module.zsjos.dal.mysql.coursecalendar.CourseCalendarEventMapper;
import cn.iocoder.yudao.module.zsjos.framework.permission.ZsjosObjectPermissionProvider;
import cn.iocoder.yudao.module.zsjos.service.calendar.CalendarNotificationAccess;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;
import java.util.Objects;
import java.util.Set;
import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants.*;

@Component
public class CourseCalendarObjectPermissionProvider implements ZsjosObjectPermissionProvider {
    public static final String BIZ_TYPE = "course-calendar";
    @Resource private CourseCalendarEventMapper mapper;
    @Resource private PermissionApi permissionApi;
    @Resource private CalendarNotificationAccess notificationAccess;

    @Override public String getBizType() { return BIZ_TYPE; }
    @Override public boolean hasPermission(Long id, String action, Long userId) {
        try { check(id, action, userId); return true; }
        catch (ServiceException denied) { return false; }
    }
    @Override public void check(Long id, String action, Long userId) {
        if (userId == null || id == null || id <= 0 || action == null
                || !Set.of("notify", "maintain").contains(action)) throw exception(CALENDAR_NOTIFY_PERMISSION_DENIED);
        var row = mapper.selectById(id);
        if (row == null || Boolean.TRUE.equals(row.getDeleted())
                || !Objects.equals(row.getTenantId(), TenantContextHolder.getRequiredTenantId()))
            throw exception(COURSE_CALENDAR_NOT_EXISTS);
        if ("notify".equals(action)) notificationAccess.checkForUser("COURSE", false, userId);
        else if (!permissionApi.hasAnyPermissions(userId, "zsjos:course-calendar:manage"))
            throw exception(CALENDAR_NOTIFY_MANAGE_PERMISSION_DENIED);
    }
}
