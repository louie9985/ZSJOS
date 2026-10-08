package cn.iocoder.yudao.module.zsjos.dal.dataobject.examcalendar;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.*;
import lombok.*;

@TableName("zsjos_exam_calendar_note")
@KeySequence("zsjos_exam_calendar_note_seq")
@Data
@EqualsAndHashCode(callSuper = true)
public class ExamCalendarNoteDO extends TenantBaseDO {
    @TableId private Long id;
    private String content;
    private Long version;
}
