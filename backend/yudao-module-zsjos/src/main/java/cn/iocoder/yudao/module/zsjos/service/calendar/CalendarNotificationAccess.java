package cn.iocoder.yudao.module.zsjos.service.calendar;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId;
import static cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants.*;

/** Shared by method security and service callers; management grants never imply notification grants. */
@Component("calendarNotificationAccess")
public class CalendarNotificationAccess {
    public static final String EXAM_NOTIFY = "zsjos:exam-calendar:notify";
    public static final String EXAM_NOTIFY_ALL = "zsjos:exam-calendar:notify-all";
    public static final String COURSE_NOTIFY = "zsjos:course-calendar:notify";
    public static final String COURSE_NOTIFY_ALL = "zsjos:course-calendar:notify-all";

    @Resource private PermissionApi permissionApi;

    public boolean check(String calendarType, boolean all) {
        return checkForUser(calendarType, all, getLoginUserId());
    }

    /** Recovery retains the original actor and rechecks current grants in the intent's tenant. */
    public boolean checkForUser(String calendarType, boolean all, Long userId) {
        boolean exam = "EXAM".equalsIgnoreCase(calendarType);
        if (!exam && !"COURSE".equalsIgnoreCase(calendarType)) throw exception(CALENDAR_NOTIFY_TYPE_INVALID);
        TenantContextHolder.getRequiredTenantId();
        if (userId == null || !permissionApi.hasAnyPermissions(userId, exam ? EXAM_NOTIFY : COURSE_NOTIFY)) {
            throw exception(CALENDAR_NOTIFY_PERMISSION_DENIED);
        }
        if (all && !permissionApi.hasAnyPermissions(userId, exam ? EXAM_NOTIFY_ALL : COURSE_NOTIFY_ALL)) {
            throw exception(CALENDAR_NOTIFY_ALL_PERMISSION_DENIED);
        }
        return true;
    }
}
