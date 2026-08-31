package cn.iocoder.yudao.module.hrm.api.employeereminder;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record HrmEmployeeReminderDispatch(LocalDate targetDate, LocalDateTime dueAt,
                                          List<HrmEmployeeReminderEmployee> employees,
                                          List<Long> recipientUserIds) {
    public boolean isEmpty() { return employees.isEmpty() || recipientUserIds.isEmpty(); }
}
