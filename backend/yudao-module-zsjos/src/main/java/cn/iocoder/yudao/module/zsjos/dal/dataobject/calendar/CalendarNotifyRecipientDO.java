package cn.iocoder.yudao.module.zsjos.dal.dataobject.calendar;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import java.time.LocalDateTime;

@TableName("zsjos_calendar_notify_recipient")
@KeySequence("zsjos_calendar_notify_recipient_seq")
@Data @EqualsAndHashCode(callSuper = true)
public class CalendarNotifyRecipientDO extends TenantBaseDO {
    @TableId private Long id;
    private Long batchId;
    private Long userId;
    private Integer userType;
    private String nicknameSnapshot;
    private String status;
    private String skipReason;
    private Long messageId;
    private LocalDateTime completedTime;
    private Boolean accepted;
    private String dedupKey;
}
