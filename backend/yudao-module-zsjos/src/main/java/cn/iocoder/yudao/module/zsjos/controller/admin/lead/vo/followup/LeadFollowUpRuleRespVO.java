package cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.followup;

import lombok.Data;

@Data
public class LeadFollowUpRuleRespVO {
    private Long id;
    private String code;
    private String name;
    private Integer firstFollowUpTimeoutMinutes;
    private Integer qualificationTimeoutMinutes;
    private Integer agingPoolTimeoutDays;
    private Integer status;
    private Integer version;
}
