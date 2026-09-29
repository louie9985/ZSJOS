package cn.iocoder.yudao.module.zsjos.service.calendar;

import cn.iocoder.yudao.module.zsjos.framework.permission.ZsjosPermission;
import org.springframework.stereotype.Service;

/** Separate proxy boundary for commands whose calendar type is selected at runtime.
 * Call after committed-operation replay, before reading/mutating a live calendar.
 * New records have no object identity and use the owning create permission instead.
 */
@Service
public class CalendarNotificationObjectAccess {
    @ZsjosPermission(bizType = "course-calendar", bizId = "#id", action = "notify")
    public void courseNotification(Long id) { }

    @ZsjosPermission(bizType = "exam-schedule", bizId = "#id", action = "notify")
    public void examNotification(Long id) { }

    @ZsjosPermission(bizType = "course-calendar", bizId = "#id", action = "maintain")
    public void courseMaintenance(Long id) { }

    @ZsjosPermission(bizType = "exam-schedule", bizId = "#id", action = "maintain")
    public void examMaintenance(Long id) { }
}
