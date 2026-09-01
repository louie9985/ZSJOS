package cn.iocoder.yudao.module.zsjos.controller.admin.personnel.vo;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class PartnerOpenRequestRespVO {

    private Long id;
    private String requestNo;
    private String partnerName;
    private String partnerMobile;
    private String maskedPartnerMobile;
    private Long assignedEmployeeUserId;
    private String assignedEmployeeName;
    private Long assignedEmployeeDeptId;
    private String assignedEmployeeDeptName;
    private Long applicantUserId;
    private String applicantName;
    private Long applicantDeptId;
    private String applicantDeptName;
    private String status;
    private String processInstanceId;
    private Long invitationId;
    private String inviteCode;
    private LocalDateTime inviteExpiresAt;
    private Long reviewedByUserId;
    private String reviewedByName;
    private LocalDateTime reviewedAt;
    private String reviewReason;
    private LocalDateTime openedAt;
    private String failureReason;
    private LocalDateTime submittedAt;
    private Long cancelledByUserId;
    private LocalDateTime cancelledAt;
    private LocalDateTime createTime;
    private Integer version;
    private List<String> availableActions;
}
