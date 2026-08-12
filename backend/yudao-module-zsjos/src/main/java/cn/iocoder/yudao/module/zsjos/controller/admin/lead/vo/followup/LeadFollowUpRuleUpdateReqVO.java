package cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.followup;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class LeadFollowUpRuleUpdateReqVO {
    @NotNull @Min(5) @Max(10080)
    private Integer firstFollowUpTimeoutMinutes;

    @NotNull @Min(5) @Max(43200)
    private Integer qualificationTimeoutMinutes;

    @NotNull @Min(1) @Max(3650)
    private Integer agingPoolTimeoutDays;
}
