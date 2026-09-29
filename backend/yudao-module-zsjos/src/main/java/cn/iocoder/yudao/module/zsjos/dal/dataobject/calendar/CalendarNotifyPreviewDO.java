package cn.iocoder.yudao.module.zsjos.dal.dataobject.calendar;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import java.time.LocalDateTime;

@TableName("zsjos_calendar_notify_preview")
@KeySequence("zsjos_calendar_notify_preview_seq")
@Data @EqualsAndHashCode(callSuper = true)
public class CalendarNotifyPreviewDO extends TenantBaseDO {
    @TableId private Long id;
    private String tokenHash;
    private Long operatorUserId;
    private String requestHash;
    private String contentHash;
    private String rosterHash;
    private LocalDateTime expiresAt;
}
