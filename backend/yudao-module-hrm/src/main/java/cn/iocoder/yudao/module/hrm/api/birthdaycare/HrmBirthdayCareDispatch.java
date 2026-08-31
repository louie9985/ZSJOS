package cn.iocoder.yudao.module.hrm.api.birthdaycare;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record HrmBirthdayCareDispatch(LocalDate targetBirthdayDate, LocalDateTime dueAt,
                                      List<HrmBirthdayCareEmployee> employees,
                                      List<Long> recipientUserIds) {
    public boolean isEmpty() {
        return employees.isEmpty() || recipientUserIds.isEmpty();
    }
}
