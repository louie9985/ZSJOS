package cn.iocoder.yudao.module.hrm.api.birthdaycare;

import java.time.LocalTime;
import java.util.List;

public record HrmBirthdayCareConfig(boolean enabled, int advanceDays, LocalTime triggerTime,
                                     List<Long> deptIds, boolean includeChildDepartments) {
    public static HrmBirthdayCareConfig defaults() {
        return new HrmBirthdayCareConfig(false, 1, LocalTime.of(9, 0), List.of(), false);
    }
}
