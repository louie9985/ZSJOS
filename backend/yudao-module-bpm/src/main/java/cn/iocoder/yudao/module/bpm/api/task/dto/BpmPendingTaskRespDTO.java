package cn.iocoder.yudao.module.bpm.api.task.dto;

import java.time.LocalDateTime;

/** Tenant-scoped live task assignment for business reminders; includes countersign child tasks. */
public record BpmPendingTaskRespDTO(String id, String taskDefinitionKey, Long assigneeUserId,
                                   LocalDateTime createTime) {}
