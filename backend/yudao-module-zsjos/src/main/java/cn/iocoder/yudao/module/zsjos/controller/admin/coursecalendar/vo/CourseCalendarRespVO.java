package cn.iocoder.yudao.module.zsjos.controller.admin.coursecalendar.vo;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;
@Data
public class CourseCalendarRespVO {
    private Long id; private String courseName; private String courseFormValue; private String courseFormLabelSnapshot;
    private LocalDateTime startTime; private LocalDateTime endTime; private String remark; private List<Long> attachmentIds;
}
