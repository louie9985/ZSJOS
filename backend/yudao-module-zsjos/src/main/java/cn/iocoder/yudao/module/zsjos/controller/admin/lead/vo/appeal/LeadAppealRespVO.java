package cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.appeal;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class LeadAppealRespVO {
    private Long id;
    private Long leadId;
    private String leadName;
    private Integer roundNo;
    private String reviewStage;
    private String status;
    private Long applicantUserId;
    private String applicantUserName;
    private String reason;
    private List<EvidenceVO> evidence;
    private String invalidReasonSnapshot;
    private String invalidDescriptionSnapshot;
    private List<EvidenceVO> invalidEvidenceSnapshot;
    private String processInstanceId;
    private String taskId;
    private Long reviewerUserId;
    private String reviewerUserName;
    private String decisionReason;
    private List<EvidenceVO> decisionEvidence;
    private LocalDateTime submittedAt;
    private LocalDateTime decidedAt;
    private Boolean canSubmitNextRound;

    @Data
    public static class EvidenceVO {
        private Long infraFileId;
        private String fileUrl;
        private String originalName;
        private String contentType;
        private Long fileSize;
        private Integer sort;
    }
}
