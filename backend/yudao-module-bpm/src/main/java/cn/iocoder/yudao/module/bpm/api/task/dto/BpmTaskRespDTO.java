package cn.iocoder.yudao.module.bpm.api.task.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class BpmTaskRespDTO {

    private String id;
    private String processInstanceId;
    private String businessKey;
    private String taskDefinitionKey;
    private String parentTaskId;
    private Boolean signTask;
    private Integer status;
    private String reason;
    private LocalDateTime createTime;
    private LocalDateTime endTime;
}
