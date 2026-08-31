package cn.iocoder.yudao.module.zsjos.dal.dataobject.lead;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@TableName("zsjos_lead_activation")
@KeySequence("zsjos_lead_activation_seq")
@Data
@EqualsAndHashCode(callSuper = true)
public class LeadActivationDO extends TenantBaseDO {
    @TableId private Long id;
    private Long personId;
    private Long leadId;
    private String sourceType;
    private Long sourceUserId;
    private Long partnerId;
    private String sourceChannelId;
    private String submissionSnapshot;
    private String notificationTargets;
    private LocalDateTime activatedAt;
    private String idempotencyKey;
}
