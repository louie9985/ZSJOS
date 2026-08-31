package cn.iocoder.yudao.module.hrm.service.birthdaycare;

import cn.iocoder.yudao.module.hrm.api.birthdaycare.HrmBirthdayCareApi;
import cn.iocoder.yudao.module.hrm.api.birthdaycare.HrmBirthdayCareConfig;

import java.util.Collection;
import java.util.List;

public interface HrmBirthdayCareService extends HrmBirthdayCareApi {
    void saveConfig(HrmBirthdayCareConfig config);

    List<Long> getRecipientUserIds(Collection<Long> deptIds);
}
