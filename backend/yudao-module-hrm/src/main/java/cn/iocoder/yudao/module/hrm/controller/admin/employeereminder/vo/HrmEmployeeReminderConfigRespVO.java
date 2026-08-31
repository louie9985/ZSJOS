package cn.iocoder.yudao.module.hrm.controller.admin.employeereminder.vo;
import lombok.Data;
import java.util.List;
@Data public class HrmEmployeeReminderConfigRespVO {
    private HrmEmployeeReminderRuleRespVO birthday;
    private HrmEmployeeReminderRuleRespVO contractExpiry;
    private HrmEmployeeReminderRuleRespVO entryAnniversary;
    @Data public static class HrmEmployeeReminderRuleRespVO extends HrmEmployeeReminderRuleVO {
        private List<Long> recipientUserIds;
        private List<Long> missingTaskPermissionUserIds;
    }
}
