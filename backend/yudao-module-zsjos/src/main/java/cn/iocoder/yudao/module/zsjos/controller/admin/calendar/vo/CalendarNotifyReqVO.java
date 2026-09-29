package cn.iocoder.yudao.module.zsjos.controller.admin.calendar.vo;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import java.util.List;

@Data
public class CalendarNotifyReqVO {
    private Long calendarId;
    @NotNull private String calendarType;
    @NotNull private String scope;
    private List<Long> userIds;
    private Boolean resend;
    private Integer calendarVersion;
    private String eventType;
    private String idempotencyKey;
    private String previewToken;
    private String title;
    private String time;
    private String remark;
    @jakarta.validation.constraints.Pattern(regexp = "CREATED|UPDATED|DELETED|PUBLISHED|REVOKED") private String maintenanceAction;
    private Boolean originalRecipients;
    @jakarta.validation.Valid private cn.iocoder.yudao.module.zsjos.controller.admin.coursecalendar.vo.CourseCalendarSaveReqVO courseContent;
}
