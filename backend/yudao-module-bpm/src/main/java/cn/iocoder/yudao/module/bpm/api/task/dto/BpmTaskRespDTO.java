package cn.iocoder.yudao.module.bpm.api.task.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class BpmTaskRespDTO {

    private String id;
    private String processInstanceId;
    private String processDefinitionKey;
    private String businessKey;
    private String taskDefinitionKey;
    private String parentTaskId;
    private Boolean signTask;
    private Integer status;
    private String reason;
    /** 当前任务所属流程定义的节点意见配置；无法读取时为 null。 */
    private Boolean reasonRequire;
    private LocalDateTime createTime;
    private LocalDateTime endTime;
}
