package cn.iocoder.yudao.module.zsjos.dal.dataobject.personalcalendar;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@TableName("zsjos_personal_calendar_event")
@KeySequence("zsjos_personal_calendar_event_seq")
@Data
@EqualsAndHashCode(callSuper = true)
public class PersonalCalendarEventDO extends TenantBaseDO {
    @TableId private Long id;
    private Long ownerUserId;
    private String title;
    private String description;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private Boolean allDay;
    private String status;
    private String sourceType;
    private Long sourceId;
    private LocalDateTime deletedTime;
}
