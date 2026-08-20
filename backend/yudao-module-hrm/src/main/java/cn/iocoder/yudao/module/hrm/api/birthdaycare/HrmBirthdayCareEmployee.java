package cn.iocoder.yudao.module.hrm.api.birthdaycare;

import java.time.LocalDate;

public record HrmBirthdayCareEmployee(Long id, String name, Long deptId, String deptName,
                                      LocalDate birthday) {
}
