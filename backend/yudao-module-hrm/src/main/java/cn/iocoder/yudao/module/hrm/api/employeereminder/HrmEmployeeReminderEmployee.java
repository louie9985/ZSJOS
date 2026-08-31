package cn.iocoder.yudao.module.hrm.api.employeereminder;

import java.time.LocalDate;

public record HrmEmployeeReminderEmployee(Long id, String name, Long deptId, String deptName,
                                          LocalDate targetDate, Long contractId, Integer anniversaryYears) {
}
