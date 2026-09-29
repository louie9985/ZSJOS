package cn.iocoder.yudao.module.zsjos.controller.admin.coursecalendar.vo;
import lombok.Data;
import jakarta.validation.constraints.*;
import java.time.LocalDateTime;
import java.util.List;
@Data
public class CourseCalendarSaveReqVO {
    @jakarta.validation.Valid private cn.iocoder.yudao.module.zsjos.controller.admin.calendar.vo.CalendarMaintenanceNotifyReqVO notification;
    @NotBlank @Size(max=200) private String courseName;
    @NotBlank @Size(max=64) private String courseFormValue;
    @NotNull private LocalDateTime startTime;
    @NotNull private LocalDateTime endTime;
    @Size(max=2000) private String remark;
    @Size(max=20) private List<@NotNull @Positive Long> attachmentIds;
}
