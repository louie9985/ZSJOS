package cn.iocoder.yudao.module.zsjos.dal.dataobject.lead;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import java.time.LocalDateTime;

@TableName("zsjos_lead_submitter_feedback_attachment")
@KeySequence("zsjos_lead_submitter_feedback_attachment_seq")
@Data
@EqualsAndHashCode(callSuper = true)
public class LeadSubmitterFeedbackAttachmentDO extends TenantBaseDO {
    @TableId private Long id;
    private Long leadId;
    private Long feedbackId;
    private Long fileId;
    private Long uploaderUserId;
    private String originalName;
    private String contentType;
    private Long fileSize;
    private LocalDateTime expiresAt;
    private Integer sort;
}

