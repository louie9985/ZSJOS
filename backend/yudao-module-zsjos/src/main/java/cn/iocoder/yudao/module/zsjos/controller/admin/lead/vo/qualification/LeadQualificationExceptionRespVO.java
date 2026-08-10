package cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.qualification;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class LeadQualificationExceptionRespVO {
    private Long id;
    private String submittedName;
    private String submittedMobile;
    private String status;
    private String assignmentStatus;
    private String handlingStage;
    private Long ownerUserId;
    private String ownerUserName;
    private Long recycleSourceOwnerUserId;
    private String recycleSourceOwnerUserName;
    private LocalDateTime qualificationDeadlineAt;
    private LocalDateTime suspendedAt;
}
