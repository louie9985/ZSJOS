package cn.iocoder.yudao.module.zsjos.controller.admin.material.vo;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Data
public class MaterialVersionRespVO {
    private Long id;
    private Long materialId;
    private Integer versionNo;
    private Long schemaVersionId;
    private String status;
    private String title;
    private Long coverFileId;
    private String coverPreviewUrl;
    private String summary;
    private Map<String, Object> values;
    private List<cn.iocoder.yudao.module.zsjos.service.material.MaterialFieldDefinition> fields;
    private Map<String, Object> dictSnapshot;
    private List<MaterialFileRespVO> files;
    private String processInstanceId;
    private String processDefinitionId;
    private String processDefinitionKey;
    private Integer processDefinitionVersion;
    private String businessKey;
    private Long submittedByUserId;
    private LocalDateTime submittedAt;
    private LocalDateTime effectiveAt;
    private LocalDateTime rejectedAt;
    private String rejectionReason;
    private Integer version;
}
