package cn.iocoder.yudao.module.hrm.api.birthdaycare;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

public interface HrmBirthdayCareApi {

    HrmBirthdayCareConfig getConfig();

    HrmBirthdayCareDispatch getDueDispatch(LocalDate today, LocalTime now);

    List<Long> getMissingTaskPermissionUserIds();
}
