package cn.iocoder.yudao.module.zsjos.dal.dataobject.examcalendar;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.*;
import lombok.*;

@TableName("zsjos_exam_calendar_note_image")
@KeySequence("zsjos_exam_calendar_note_image_seq")
@Data
@EqualsAndHashCode(callSuper = true)
public class ExamCalendarNoteImageDO extends TenantBaseDO {
    @TableId private Long id;
    private Long fileId;
    private Long uploadedBy;
    private Boolean bound;
}
