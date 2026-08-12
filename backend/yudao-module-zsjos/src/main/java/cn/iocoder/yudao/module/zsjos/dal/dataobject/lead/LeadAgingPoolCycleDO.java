package cn.iocoder.yudao.module.zsjos.dal.dataobject.lead;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@TableName("zsjos_lead_aging_pool_cycle")
@KeySequence("zsjos_lead_aging_pool_cycle_seq")
@Data
@EqualsAndHashCode(callSuper = true)
public class LeadAgingPoolCycleDO extends TenantBaseDO {
    @TableId private Long id;
    private Long leadId;
    private Integer cycleNo;
    private Long originalOwnerUserId;
    private Long collaboratorUserId;
    private Long frozenDeptId;
    private String status;
    private LocalDateTime ownershipStartedAt;
    private LocalDateTime dueAt;
    private LocalDateTime enteredAt;
    private LocalDateTime assignedAt;
    private LocalDateTime exitedAt;
    private LocalDateTime convertedAt;
    private String exitReason;
    private String idempotencyKey;
    private Integer version;
}
