package cn.iocoder.yudao.module.hrm.api.employeereminder;

import java.time.LocalTime;
import java.util.List;

public record HrmEmployeeReminderRule(boolean enabled, int advanceDays, LocalTime triggerTime,
                                      List<Long> deptIds, boolean includeChildDepartments) {
    public static HrmEmployeeReminderRule defaults() {
        return new HrmEmployeeReminderRule(false, 1, LocalTime.of(9, 0), List.of(), false);
    }
}
