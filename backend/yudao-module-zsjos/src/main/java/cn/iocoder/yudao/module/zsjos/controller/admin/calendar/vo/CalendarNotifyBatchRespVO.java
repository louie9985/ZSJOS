package cn.iocoder.yudao.module.zsjos.controller.admin.calendar.vo;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class CalendarNotifyBatchRespVO {
    private Long id; private String calendarType; private Long calendarId; private Integer calendarVersion;
    private String eventType; private String scope; private String titleSnapshot; private String timeSnapshot;
    private String remarkSnapshot; private String status; private Boolean resend;
    private Integer requestedCount; private Integer acceptedCount; private Integer skippedCount;
    private LocalDateTime createTime;
}
