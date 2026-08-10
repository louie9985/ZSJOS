package cn.iocoder.yudao.module.zsjos.controller.admin.task.vo;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class BusinessTaskRespVO {
    private Long id;
    private String taskType;
    private String bizType;
    private Long bizId;
    private String title;
    private String summary;
    private LocalDateTime dueAt;
    private Boolean overdue;
    private String actionCode;
}
