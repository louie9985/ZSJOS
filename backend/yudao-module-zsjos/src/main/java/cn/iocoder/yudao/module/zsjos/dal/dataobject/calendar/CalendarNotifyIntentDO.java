package cn.iocoder.yudao.module.zsjos.dal.dataobject.calendar;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import java.time.LocalDateTime;

@TableName("zsjos_calendar_notify_intent")
@KeySequence("zsjos_calendar_notify_intent_seq")
@Data @EqualsAndHashCode(callSuper = true)
public class CalendarNotifyIntentDO extends TenantBaseDO {
    @TableId private Long id;
    private String operationKey;
    private String requestHash;
    private String acceptanceKey;
    private Long operatorUserId;
    private String calendarType;
    private Long calendarId;
    private Long snapshotId;
    private String eventType;
    private String scope;
    private String recipientsJson;
    private Boolean resend;
    private String status;
    private Long batchId;
    private Integer attemptCount;
    private LocalDateTime nextAttemptAt;
    private String lastErrorCode;
    private LocalDateTime completedTime;
}
