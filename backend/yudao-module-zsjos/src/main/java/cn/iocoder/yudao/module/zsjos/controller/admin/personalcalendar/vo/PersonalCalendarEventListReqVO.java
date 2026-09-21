package cn.iocoder.yudao.module.zsjos.controller.admin.personalcalendar.vo;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class PersonalCalendarEventListReqVO {
    private String readScope;
    private Long targetUserId;
    @NotNull private LocalDateTime rangeStart;
    @NotNull private LocalDateTime rangeEnd;
}
