package cn.iocoder.yudao.module.zsjos.dal.dataobject.calendar;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

@TableName("zsjos_calendar_notify_state")
@KeySequence("zsjos_calendar_notify_state_seq")
@Data @EqualsAndHashCode(callSuper = true)
public class CalendarNotifyStateDO extends TenantBaseDO {
    @TableId private Long id;
    private String calendarType;
    private Long calendarId;
    private Integer calendarVersion;
    private Long currentSnapshotId;
    private String recordStatus;
}
