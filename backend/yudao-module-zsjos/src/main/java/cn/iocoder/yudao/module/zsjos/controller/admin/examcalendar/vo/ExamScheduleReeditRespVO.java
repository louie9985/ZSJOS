package cn.iocoder.yudao.module.zsjos.controller.admin.examcalendar.vo;

import lombok.Data;
import java.time.LocalDate;

@Data
public class ExamScheduleReeditRespVO {
    private String scheduleName;
    private String scheduleType;
    private LocalDate exactDate;
    private LocalDate startDate;
    private LocalDate endDate;
    private String remark;
}
