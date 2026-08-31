package cn.iocoder.yudao.module.bpm.api.task.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class BpmProcessNodeStatusRespDTO {
    private String taskDefinitionKey;
    private String status;
    private Long reviewerUserId;
    private String reviewerUserName;
    private LocalDateTime createTime;
    private LocalDateTime endTime;
}
