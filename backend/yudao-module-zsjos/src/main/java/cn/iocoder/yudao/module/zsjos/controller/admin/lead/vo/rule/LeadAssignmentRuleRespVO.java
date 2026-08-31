package cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.rule;

import lombok.Data;

@Data
public class LeadAssignmentRuleRespVO {
    private Long id;
    private String code;
    private String name;
    private String strategyType;
    private Integer acceptTimeoutSeconds;
    private Integer maxAttempts;
    private Integer dailyClaimLimit;
    private Integer status;
}
