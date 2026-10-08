package cn.iocoder.yudao.module.bpm.api.task.dto;

import java.time.LocalDateTime;
import java.util.List;

/** Read-only business projection; never exposes process variables or task commands. */
public record BpmProcessProgressDTO(Integer status, List<Node> nodes, List<PendingTask> currentTasks) {
    public record User(Long id, String name) {}
    public record PendingTask(String id, String name, String nodeId, Long assigneeUserId,
                              String assigneeName, LocalDateTime createTime) {}
    public record Task(String id, String parentTaskId, Integer status, String assigneeName,
                       String ownerName, LocalDateTime createTime, LocalDateTime endTime, String reason) {}
    public record Node(String id, String name, Integer status, LocalDateTime startTime,
                       LocalDateTime endTime, List<User> candidates, List<Task> tasks) {}
}
