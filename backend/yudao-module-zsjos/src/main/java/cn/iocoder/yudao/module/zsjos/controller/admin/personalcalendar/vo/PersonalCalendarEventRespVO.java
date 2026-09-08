package cn.iocoder.yudao.module.zsjos.controller.admin.personalcalendar.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class PersonalCalendarEventRespVO {
    private Long id;
    private String title;
    private String description;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private Boolean allDay;
    private String status;
    private String sourceType;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
