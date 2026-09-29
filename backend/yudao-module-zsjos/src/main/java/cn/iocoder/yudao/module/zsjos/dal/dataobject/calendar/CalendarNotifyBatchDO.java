package cn.iocoder.yudao.module.zsjos.dal.dataobject.calendar;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import java.time.LocalDateTime;

@TableName("zsjos_calendar_notify_batch")
@KeySequence("zsjos_calendar_notify_batch_seq")
@Data @EqualsAndHashCode(callSuper = true)
public class CalendarNotifyBatchDO extends TenantBaseDO {
    @TableId private Long id;
    private String calendarType;
    private Long calendarId;
    private Integer calendarVersion;
    private String eventType;
    private String scope;
    private String titleSnapshot;
    private String timeSnapshot;
    private String remarkSnapshot;
    private String sourceEventKey;
    private Boolean resend;
    private String status;
    private Long snapshotId;
    private Long operatorUserId;
    private String idempotencyKey;
    private String requestHash;
    private Integer requestedCount;
    private Integer acceptedCount;
    private Integer skippedCount;
}
