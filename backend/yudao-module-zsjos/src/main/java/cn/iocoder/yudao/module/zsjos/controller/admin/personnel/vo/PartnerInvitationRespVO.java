package cn.iocoder.yudao.module.zsjos.controller.admin.personnel.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class PartnerInvitationRespVO {

    private Long id;
    private String inviteCode;
    private String name;
    private String mobile;
    private Long assignedOperatorUserId;
    private String assignedOperatorName;
    private String status;
    private LocalDateTime expiresAt;
    private LocalDateTime usedAt;
    private LocalDateTime voidedAt;
    private Long partnerId;
    private Long createdByUserId;
    private String createdByName;
    private LocalDateTime createTime;
    private Integer version;
}
