package cn.iocoder.yudao.module.zsjos.controller.admin.calendarrestday.vo;

import jakarta.validation.constraints.*;
import lombok.Data;
import java.time.LocalDate;

@Data
public class CalendarRestDayChangeVO {
    @NotNull private LocalDate date;
    @NotNull private Boolean rest;
    @NotNull @Min(0) private Long version;
}
