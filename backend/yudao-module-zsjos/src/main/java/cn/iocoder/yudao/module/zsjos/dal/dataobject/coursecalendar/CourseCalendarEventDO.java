package cn.iocoder.yudao.module.zsjos.dal.dataobject.coursecalendar;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import java.time.LocalDateTime;

@TableName("zsjos_course_calendar_event")
@KeySequence("zsjos_course_calendar_event_seq")
@Data
@EqualsAndHashCode(callSuper = true)
public class CourseCalendarEventDO extends TenantBaseDO {
    @TableId private Long id;
    private String courseName;
    private String courseFormValue;
    private String courseFormLabelSnapshot;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private String remark;
    private String attachmentIdsJson;
}
