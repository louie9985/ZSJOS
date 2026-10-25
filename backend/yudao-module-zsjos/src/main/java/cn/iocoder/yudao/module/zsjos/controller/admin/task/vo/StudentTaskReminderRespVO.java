package cn.iocoder.yudao.module.zsjos.controller.admin.task.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@AllArgsConstructor
public class StudentTaskReminderRespVO {
    private Long id;
    private String taskType;
    private String title;
    private LocalDateTime dueAt;
    private boolean overdue;
}
