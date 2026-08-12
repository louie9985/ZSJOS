package cn.iocoder.yudao.module.zsjos.dal.dataobject.lead;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@TableName("zsjos_lead_aging_pool_notify_stage")
@KeySequence("zsjos_lead_aging_pool_notify_stage_seq")
@Data
@EqualsAndHashCode(callSuper = true)
public class LeadAgingPoolNotifyStageDO extends TenantBaseDO {
    @TableId private Long id;
    private Long leadId;
    private Integer cycleNo;
    private Long notifyRuleId;
    private String stage;
    private String status;
    private Integer attemptCount;
    private LocalDateTime nextRetryAt;
    private String lastErrorCode;
    private LocalDateTime sentAt;
    private LocalDateTime emittedAt;
}
