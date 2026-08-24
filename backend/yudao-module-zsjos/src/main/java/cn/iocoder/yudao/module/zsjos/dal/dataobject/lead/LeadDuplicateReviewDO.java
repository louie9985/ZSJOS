package cn.iocoder.yudao.module.zsjos.dal.dataobject.lead;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@TableName("zsjos_lead_duplicate_review")
@KeySequence("zsjos_lead_duplicate_review_seq")
@Data
@EqualsAndHashCode(callSuper = true)
public class LeadDuplicateReviewDO extends TenantBaseDO {
    @TableId private Long id;
    private String status;
    private Long submitterUserId;
    private String submissionSourceType;
    private Long submissionPartnerId;
    private String submissionSnapshot;
    private String leadCategoryLabelSnapshot;
    private String matchRules;
    private String candidateSnapshot;
    private Long matchedPersonId;
    private Long matchedLeadId;
    private String resultType;
    private String reviewOpinion;
    private String reviewAttachments;
    private Long selectedSalesUserId;
    private Long reviewerUserId;
    private LocalDateTime reviewedAt;
    private String beforeSnapshot;
    private String afterSnapshot;
    private String submissionIdempotencyKey;
    private String decisionIdempotencyKey;
    private Integer version;
}
