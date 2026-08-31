package cn.iocoder.yudao.module.hrm.api.employeereminder;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

public interface HrmEmployeeReminderApi {
    HrmEmployeeReminderConfig getConfig();
    HrmEmployeeReminderDispatch getDueBirthdayDispatch(LocalDate today, LocalTime now);
    HrmEmployeeReminderDispatch getDueContractExpiryDispatch(LocalDate today, LocalTime now);
    HrmEmployeeReminderDispatch getDueEntryAnniversaryDispatch(LocalDate today, LocalTime now);
    List<Long> getMissingTaskPermissionUserIds(HrmEmployeeReminderRule rule);
    List<Long> getRecipientUserIds(java.util.Collection<Long> deptIds);
}
