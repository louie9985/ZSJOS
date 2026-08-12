package cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.rule;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class LeadAssignmentRuleUpdateReqVO {
    @NotNull @Min(10) @Max(3600) private Integer acceptTimeoutSeconds;
    @NotNull @Min(1) @Max(20) private Integer maxAttempts;
    @NotNull @Min(1) @Max(100) private Integer dailyClaimLimit;
}
