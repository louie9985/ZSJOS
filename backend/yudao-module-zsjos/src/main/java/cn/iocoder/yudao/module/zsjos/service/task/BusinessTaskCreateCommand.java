package cn.iocoder.yudao.module.zsjos.service.task;

import java.time.LocalDateTime;

public record BusinessTaskCreateCommand(
        String taskType,
        String bizType,
        Long bizId,
        Long assigneeId,
        String title,
        String summary,
        String actionCode,
        LocalDateTime dueAt,
        LocalDateTime remindAt,
        String payload,
        String idempotencyKey) {
}
