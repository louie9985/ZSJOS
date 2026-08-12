package cn.iocoder.yudao.module.zsjos.dal.dataobject.lead;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import java.time.LocalDateTime;

@TableName("zsjos_lead_complaint") @KeySequence("zsjos_lead_complaint_seq") @Data @EqualsAndHashCode(callSuper = true)
public class LeadComplaintDO extends TenantBaseDO {
    @TableId private Long id; private Long leadId; private Long complainantUserId; private Long salesUserId;
    private String reason; private String evidenceRefs; private String status; private String result;
    private Long handlerUserId; private String handlerOpinion; private String handlerEvidenceRefs;
    private LocalDateTime handledAt; private String createIdempotencyKey; private String decisionIdempotencyKey; private Integer version;
}
