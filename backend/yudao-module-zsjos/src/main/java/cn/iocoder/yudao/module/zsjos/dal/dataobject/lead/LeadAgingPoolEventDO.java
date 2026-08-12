package cn.iocoder.yudao.module.zsjos.dal.dataobject.lead;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@TableName("zsjos_lead_aging_pool_event")
@KeySequence("zsjos_lead_aging_pool_event_seq")
@Data
@EqualsAndHashCode(callSuper = true)
public class LeadAgingPoolEventDO extends TenantBaseDO {
    @TableId private Long id;
    private Long cycleId;
    private Long leadId;
    private String eventType;
    private Long operatorUserId;
    private Long previousCollaboratorUserId;
    private Long collaboratorUserId;
    private String reason;
    private String idempotencyKey;
    private LocalDateTime occurredAt;
}
