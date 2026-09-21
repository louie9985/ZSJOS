package cn.iocoder.yudao.module.bpm.api.task.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class BpmTaskRespDTO {
    private Long actionUserId;
    private String actionUserNameSnapshot;


    private String id;
    private String processInstanceId;
    private String processDefinitionKey;
    private String businessKey;
    private String taskDefinitionKey;
    private String parentTaskId;
    private Boolean signTask;
    private Integer status;
    private String reason;
    /** 当前任务通过意见的有效要求（业务策略优先，未覆盖时沿用定义）；无法读取时为 null。 */
    private Boolean reasonRequire;
    private LocalDateTime createTime;
    private LocalDateTime endTime;
}
