package cn.iocoder.yudao.module.zsjos.dal.dataobject.lead;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@TableName("zsjos_lead_submitter_assist_request")
@KeySequence("zsjos_lead_submitter_assist_request_seq")
@Data
@EqualsAndHashCode(callSuper = true)
public class LeadSubmitterAssistRequestDO extends TenantBaseDO {
    @TableId private Long id;
    private Long leadId;
    private String leadNoSnapshot;
    private Long requesterUserId;
    private String problem;
    private String expectedAssistance;
    private String remark;
    private String attachmentSnapshotsJson;
    private String submitterTypeSnapshot;
    private Long submitterIdSnapshot;
    private String submitterNameSnapshot;
    private Long assigneeUserIdSnapshot;
    private String assigneeNameSnapshot;
    private LocalDateTime requestedAt;
    private String requestFingerprint;
    private String idempotencyKey;
}
