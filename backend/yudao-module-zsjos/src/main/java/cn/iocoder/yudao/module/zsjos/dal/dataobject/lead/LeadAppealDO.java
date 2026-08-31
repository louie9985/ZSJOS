package cn.iocoder.yudao.module.zsjos.dal.dataobject.lead;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@TableName("zsjos_lead_appeal")
@KeySequence("zsjos_lead_appeal_seq")
@Data
@EqualsAndHashCode(callSuper = true)
public class LeadAppealDO extends TenantBaseDO {
    @TableId private Long id;
    private Long leadId;
    private Integer roundNo;
    private String reviewStage;
    private String status;
    private Long ownerUserIdSnapshot;
    private Long ownerDeptIdSnapshot;
    private Long reviewerDeptIdSnapshot;
    private String reviewerUserIdsSnapshot;
    private Long applicantUserId;
    private Long partnerId;
    private String reason;
    private String evidenceRefs;
    private String invalidReasonSnapshot;
    private String invalidDescriptionSnapshot;
    private String invalidEvidenceRefsSnapshot;
    private String processInstanceId;
    private Long reviewerUserId;
    private String decisionReason;
    private String decisionEvidenceRefs;
    private LocalDateTime submittedAt;
    private LocalDateTime decidedAt;
    private String submissionIdempotencyKey;
    private String decisionIdempotencyKey;
}
