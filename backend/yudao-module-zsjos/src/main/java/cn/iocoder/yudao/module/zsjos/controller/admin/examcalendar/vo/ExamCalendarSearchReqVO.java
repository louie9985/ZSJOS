package cn.iocoder.yudao.module.zsjos.controller.admin.examcalendar.vo;

import cn.iocoder.yudao.module.zsjos.controller.admin.calendar.vo.CalendarSearchReqVO;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class ExamCalendarSearchReqVO extends CalendarSearchReqVO {
    @jakarta.validation.constraints.Pattern(regexp = "DRAFT|PUBLISHED|REVOKED") private String recordStatus;
}

