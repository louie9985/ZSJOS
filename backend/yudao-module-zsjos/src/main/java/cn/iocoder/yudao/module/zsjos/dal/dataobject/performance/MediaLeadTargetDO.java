package cn.iocoder.yudao.module.zsjos.dal.dataobject.performance;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;

@TableName("zsjos_media_lead_target")
@KeySequence("zsjos_media_lead_target_seq")
@Data
@EqualsAndHashCode(callSuper = true)
public class MediaLeadTargetDO extends TenantBaseDO {
    @TableId private Long id;
    private String scopeType;
    private Long scopeId;
    private Long deptId;
    private Long centerId;
    private LocalDate periodStart;
    private Integer targetCount;
    private Boolean manual;
    private String reason;
    private Integer version;
}
