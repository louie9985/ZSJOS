package cn.iocoder.yudao.module.zsjos.controller.admin.calendarrestday.vo;

import lombok.Data;
import java.time.LocalDate;

@Data
public class CalendarRestDayRespVO {
    private LocalDate date;
    private Boolean rest;
    private Long version;
}
