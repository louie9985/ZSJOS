package cn.iocoder.yudao.module.zsjos.dal.dataobject.lead;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/** Manual public-sea collaboration marker. It never replaces Lead assignment state. */
@TableName("zsjos_lead_public_sea_record")
@KeySequence("zsjos_lead_public_sea_record_seq")
@Data
@EqualsAndHashCode(callSuper = true)
public class LeadPublicSeaRecordDO extends TenantBaseDO {
    @TableId private Long id;
    private Long leadId;
    private Long ownerUserId;
    private Long collaboratorUserId;
    private Long releasedByUserId;
    private LocalDateTime releasedAt;
    private String releaseReason;
}
