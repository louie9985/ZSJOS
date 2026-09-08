package cn.iocoder.yudao.module.zsjos.controller.admin.personalcalendar.vo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class PersonalCalendarEventSaveReqVO {
    @NotBlank @Size(max = 100) private String title;
    @Size(max = 2000) private String description;
    @NotNull private LocalDateTime startTime;
    @NotNull private LocalDateTime endTime;
    @NotNull private Boolean allDay;
}
