package cn.iocoder.yudao.module.zsjos.controller.admin.personnel.vo;

import lombok.Data;

@Data
public class PartnerStudentInvitationContextRespVO {
    private boolean opened;
    private Long defaultOperatorUserId;
    private boolean operatorAssignmentConflict;
    private PartnerInvitationRespVO invitation;
}
