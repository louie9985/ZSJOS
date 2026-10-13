package cn.iocoder.yudao.module.bpm.api.task.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class BpmProcessNodeStatusRespDTO {
    private Long actionUserId;
    private String actionUserNameSnapshot;
    /** Proven system approval under this task's frozen consecutive-deduplication policy; never a human action. */
    private Boolean consecutiveApprovalDeduplicated;

    private String taskDefinitionKey;
    private String status;
    private Long reviewerUserId;
    private String reviewerUserName;
    private LocalDateTime createTime;
    private LocalDateTime endTime;
}
