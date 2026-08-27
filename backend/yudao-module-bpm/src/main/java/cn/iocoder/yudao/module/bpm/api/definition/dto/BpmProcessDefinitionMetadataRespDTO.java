package cn.iocoder.yudao.module.bpm.api.definition.dto;

import lombok.Data;

@Data
public class BpmProcessDefinitionMetadataRespDTO {
    private String id;
    private String key;
    private String name;
    private Integer version;
    private String deploymentId;
    private Boolean suspended;
    private Long formId;
    private String description;
}
