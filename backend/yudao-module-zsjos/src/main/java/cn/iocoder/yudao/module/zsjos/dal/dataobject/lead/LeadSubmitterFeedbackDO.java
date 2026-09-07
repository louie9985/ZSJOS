package cn.iocoder.yudao.module.zsjos.dal.dataobject.lead;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

@TableName("zsjos_lead_submitter_feedback")
@KeySequence("zsjos_lead_submitter_feedback_seq")
@Data
@EqualsAndHashCode(callSuper = true)
public class LeadSubmitterFeedbackDO extends TenantBaseDO {
    @TableId private Long id;
    private Long leadId;
    private String submitterSubjectType;
    private Long submitterUserId;
    private Long partnerAccountId;
    private Long partnerId;
    private Long salesUserId;
    private String salesNameSnapshot;
    private String submitterNameSnapshot;
    private String feedback;
    private Integer requestVersion;
    private String idempotencyKey;
    private String requestFingerprint;
}

