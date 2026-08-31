package cn.iocoder.yudao.module.hrm.controller.admin.employeereminder.vo;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
@Data public class HrmEmployeeReminderConfigSaveReqVO {
    @Valid @NotNull private HrmEmployeeReminderRuleVO birthday;
    @Valid @NotNull private HrmEmployeeReminderRuleVO contractExpiry;
    @Valid @NotNull private HrmEmployeeReminderRuleVO entryAnniversary;
}
