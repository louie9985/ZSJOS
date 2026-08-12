package cn.iocoder.yudao.module.zsjos.controller.admin.workplan.vo;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Data
public class WorkTaskRespVO {
    private Long id;
    private Long planId;
    private Long parentTaskId;
    private String title;
    private String description;
    private String deliverableRequirement;
    private Long assigneeUserId;
    private Long assigneeDeptId;
    private Long assignerUserId;
    private LocalDateTime dueAt;
    private LocalDateTime remindAt;
    private Boolean confirmationRequired;
    private Long confirmerUserId;
    private String status;
    private LocalDateTime reportedAt;
    private LocalDateTime completedAt;
    private LocalDateTime cancelledAt;
    private String cancelReason;
    private Integer version;
    private Boolean blockedByChildren;
    private Integer completedChildCount;
    private Integer totalChildCount;
    private Map<String, Object> taskFields;
    private List<WorkReportRespVO> reports;
    private List<String> availableActions;
}
