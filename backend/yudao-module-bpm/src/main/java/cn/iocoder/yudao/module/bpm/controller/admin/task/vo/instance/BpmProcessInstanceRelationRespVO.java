package cn.iocoder.yudao.module.bpm.controller.admin.task.vo.instance;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class BpmProcessInstanceRelationRespVO {
    private Long id;
    private String formField;
    private Integer sort;
    private String targetProcessInstanceId;
    private String name;
    private String processDefinitionId;
    private String processDefinitionName;
    private String processDefinitionKey;
    private String displayNo;
    private String businessKey;
    private String startUserName;
    private LocalDateTime startTime;
    private Integer status;
    private Boolean detailAvailable;
}
