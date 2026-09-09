package cn.iocoder.yudao.module.zsjos.controller.admin.coursecalendar.vo;
import lombok.Data;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;
@Data
public class CourseCalendarPageReqVO {
    @NotNull private LocalDateTime rangeStart;
    @NotNull private LocalDateTime rangeEnd;
}
