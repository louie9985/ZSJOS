package cn.iocoder.yudao.module.zsjos.controller.admin.registration.vo;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class StudentContactExtensionRespVO {
    private Long id;
    private Long serviceRelationId;
    private Long taskId;
    private String status;
    private LocalDateTime originalDueAt;
    private LocalDateTime requestedDueAt;
    private String reasonValue;
    private String reasonLabel;
    private String description;
    private List<Long> attachmentFileIds;
    private Long applicantUserId;
    private Long reviewerUserId;
    private String processInstanceId;
    private String decisionReason;
    private LocalDateTime submittedAt;
    private LocalDateTime resolvedAt;
}
