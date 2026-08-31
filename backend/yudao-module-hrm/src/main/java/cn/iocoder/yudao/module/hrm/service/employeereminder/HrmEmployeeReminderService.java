package cn.iocoder.yudao.module.hrm.service.employeereminder;

import cn.iocoder.yudao.module.hrm.api.employeereminder.HrmEmployeeReminderApi;
import cn.iocoder.yudao.module.hrm.api.employeereminder.HrmEmployeeReminderConfig;

public interface HrmEmployeeReminderService extends HrmEmployeeReminderApi {
    void saveConfig(HrmEmployeeReminderConfig config);
}
