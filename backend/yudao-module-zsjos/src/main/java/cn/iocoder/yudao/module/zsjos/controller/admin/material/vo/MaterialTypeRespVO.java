package cn.iocoder.yudao.module.zsjos.controller.admin.material.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class MaterialTypeRespVO {
    private Long id;
    private String name;
    private String code;
    private String description;
    private Integer status;
    private Long currentSchemaVersionId;
    private String bpmProcessDefinitionKey;
    private Boolean allowManualCreate;
    private Boolean allowImport;
    private Boolean allowAutoCollect;
    private Boolean recommendationEnabled;
    private java.util.Map<String, Object> recommendationConfig;
    private Integer version;
    private MaterialTemplateRespVO currentSchema;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
