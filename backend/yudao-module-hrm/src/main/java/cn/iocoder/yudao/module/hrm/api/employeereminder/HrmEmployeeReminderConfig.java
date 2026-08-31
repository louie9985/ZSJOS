package cn.iocoder.yudao.module.hrm.api.employeereminder;

public record HrmEmployeeReminderConfig(HrmEmployeeReminderRule birthday,
                                        HrmEmployeeReminderRule contractExpiry,
                                        HrmEmployeeReminderRule entryAnniversary) {
    public static HrmEmployeeReminderConfig defaults() {
        return new HrmEmployeeReminderConfig(HrmEmployeeReminderRule.defaults(),
                HrmEmployeeReminderRule.defaults(), HrmEmployeeReminderRule.defaults());
    }
}
