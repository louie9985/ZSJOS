package cn.iocoder.yudao.module.bpm.api.task.dto;

import lombok.Data;

@Data
public class BpmTaskSignReqDTO {
    private String taskId;
    private Long assigneeUserId;
    private String reason;
}
