package cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.subordinate;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class SubordinateTaskRespVO {
    private Long id;
    private String taskType;
    private Long leadId;
    private String leadNo;
    private String leadName;
    private LocalDateTime dueAt;
    private Boolean overdue;
}
