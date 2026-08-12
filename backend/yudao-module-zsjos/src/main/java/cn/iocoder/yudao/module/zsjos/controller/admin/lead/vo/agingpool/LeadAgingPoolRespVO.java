package cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.agingpool;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class LeadAgingPoolRespVO {
    private Long cycleId;
    private Long leadId;
    private Integer cycleNo;
    private String status;
    private Long originalOwnerUserId;
    private String originalOwnerUserName;
    private Long collaboratorUserId;
    private String collaboratorUserName;
    private Long frozenDeptId;
    private String frozenDeptName;
    private String submittedName;
    private String submittedMobile;
    private String submittedWechatId;
    private String leadCategory;
    private String sourceChannel;
    private LocalDateTime ownershipStartedAt;
    private LocalDateTime dueAt;
    private LocalDateTime enteredAt;
    private LocalDateTime assignedAt;
    private LocalDateTime lastFollowUpAt;
    private LocalDateTime nextFollowUpAt;
    private Long activeSalesOrderId;
    private String activeSalesOrderStatus;
    private List<String> availableActions;
}
