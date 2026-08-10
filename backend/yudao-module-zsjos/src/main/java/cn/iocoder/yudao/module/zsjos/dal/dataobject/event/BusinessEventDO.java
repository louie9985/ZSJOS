package cn.iocoder.yudao.module.zsjos.dal.dataobject.event;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@TableName("zsjos_business_event")
@KeySequence("zsjos_business_event_seq")
@Data
@EqualsAndHashCode(callSuper = true)
public class BusinessEventDO extends TenantBaseDO {
    @TableId private Long id;
    private String eventType;
    private String aggregateType;
    private Long aggregateId;
    private Long operatorUserId;
    private String fromStatus;
    private String toStatus;
    private String reason;
    private String evidenceRefs;
    private String relatedObjectRefs;
    private LocalDateTime occurredAt;
    private String idempotencyKey;
}
