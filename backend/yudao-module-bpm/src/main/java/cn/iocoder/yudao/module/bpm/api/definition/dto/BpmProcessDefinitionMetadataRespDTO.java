package cn.iocoder.yudao.module.bpm.api.definition.dto;

import lombok.Data;

import java.util.List;

@Data
public class BpmProcessDefinitionMetadataRespDTO {
    private String id;
    private String key;
    private String name;
    private Integer version;
    private String deploymentId;
    private Boolean suspended;
    private String category;
    private Long formId;
    private String description;
    private Boolean simpleSequentialApproval;
    private List<BpmUserTaskMetadataRespDTO> userTasks;
}
