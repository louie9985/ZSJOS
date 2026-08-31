package cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.duplicate;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class LeadDuplicateReviewRespVO {
    private Long id;
    private String status;
    private Long submitterUserId;
    private String submissionSnapshot;
    private String duplicateFlag;
    private String duplicateResult;
    private String primaryRuleCode;
    private String reviewFingerprint;
    private String matchRules;
    private String candidateSnapshot;
    private String resultType;
    private String reviewOpinion;
    private String reviewAttachments;
    private Long selectedSalesUserId;
    private Long reviewerUserId;
    private LocalDateTime reviewedAt;
    private String beforeSnapshot;
    private String afterSnapshot;
    private LocalDateTime createTime;
}
