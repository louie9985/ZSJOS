package cn.iocoder.yudao.module.system.dal.dataobject.notify;

import cn.iocoder.yudao.framework.mybatis.core.dataobject.BaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@TableName("system_notify_business_outbox")
@KeySequence("system_notify_business_outbox_seq")
@Data
@EqualsAndHashCode(callSuper = true)
public class NotifyBusinessOutboxDO extends BaseDO {
    @TableId private Long id;
    private Long tenantId;
    private String sceneCode;
    private String sourceEventKey;
    private Long targetRuleId;
    private String bizType;
    private Long bizId;
    private Long operatorUserId;
    private LocalDateTime occurredAt;
    private String payload;
    private String status;
    private Integer attemptCount;
    private LocalDateTime nextAttemptAt;
    private LocalDateTime leaseUntil;
    private String claimToken;
    private String lastError;
    private LocalDateTime succeededAt;
}
