package cn.iocoder.yudao.module.zsjos.dal.dataobject.lead;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/** Historical public-sea compatibility evidence. New public-sea operations use the cycle table. */
@TableName("zsjos_lead_public_sea_pool_compatibility_record")
@KeySequence("zsjos_lead_public_sea_pool_compatibility_record_seq")
@Data
@EqualsAndHashCode(callSuper = true)
public class LeadPublicSeaPoolCompatibilityRecordDO extends TenantBaseDO {
    @TableId private Long id;
    private Long leadId;
    private Long ownerUserId;
    private Long collaboratorUserId;
    private Long releasedByUserId;
    private LocalDateTime releasedAt;
    private String releaseReason;
}
