package cn.iocoder.yudao.module.zsjos.dal.dataobject.calendar;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

@TableName("zsjos_calendar_notify_snapshot")
@KeySequence("zsjos_calendar_notify_snapshot_seq")
@Data @EqualsAndHashCode(callSuper = true)
public class CalendarNotifySnapshotDO extends TenantBaseDO {
    @TableId private Long id;
    private String calendarType;
    private Long calendarId;
    private Integer calendarVersion;
    private String eventType;
    private String recordStatus;
    private String titleSnapshot;
    private String timeSnapshot;
    private String remarkSnapshot;
    private String detailsJson;
    private String contentHash;
}
