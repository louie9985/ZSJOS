package cn.iocoder.yudao.module.zsjos.controller.admin.personalcalendar.vo;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class PersonalCalendarEventListReqVO {
    @NotNull private LocalDateTime rangeStart;
    @NotNull private LocalDateTime rangeEnd;
}
