package cn.iocoder.yudao.module.bpm.api.task.dto;

import lombok.Data;
import lombok.experimental.Accessors;

/** BPM 任务动作前置校验上下文，不暴露 Flowable 内部类型。 */
@Data
@Accessors(chain = true)
public class BpmTaskActionContext {

    private Long userId;
    private String action;
    private String reason;
    private String taskId;
    private String parentTaskId;
    private String taskDefinitionKey;
    private String processInstanceId;
    private String processDefinitionId;
    private String processDefinitionKey;
    private Integer processDefinitionVersion;
    private String businessKey;
    private String tenantId;

}
