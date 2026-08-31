package cn.iocoder.yudao.module.zsjos.dal.dataobject.lead;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import java.time.LocalDate;
import java.time.LocalDateTime;

@TableName("zsjos_lead_urge") @KeySequence("zsjos_lead_urge_seq") @Data @EqualsAndHashCode(callSuper = true)
public class LeadUrgeDO extends TenantBaseDO {
    @TableId private Long id; private Long leadId; private Long submitterUserId; private Long partnerId; private Long targetSalesUserId;
    private LocalDate urgeDate; private String reason; private LocalDateTime urgedAt;
}
