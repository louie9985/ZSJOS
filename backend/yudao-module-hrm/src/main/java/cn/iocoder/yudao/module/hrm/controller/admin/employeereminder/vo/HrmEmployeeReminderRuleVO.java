package cn.iocoder.yudao.module.hrm.controller.admin.employeereminder.vo;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import java.util.List;

@Data
public class HrmEmployeeReminderRuleVO {
    @NotNull private Boolean enabled;
    @Min(0) @Max(30) private Integer advanceDays;
    @NotBlank private String triggerTime;
    private List<Long> deptIds;
    private Boolean includeChildDepartments;
}
