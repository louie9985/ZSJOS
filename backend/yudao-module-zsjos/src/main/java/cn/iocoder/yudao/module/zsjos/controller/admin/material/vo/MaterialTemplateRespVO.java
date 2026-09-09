package cn.iocoder.yudao.module.zsjos.controller.admin.material.vo;

import cn.iocoder.yudao.module.zsjos.service.material.MaterialFieldDefinition;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Data
public class MaterialTemplateRespVO {
    private Long id;
    private Long materialTypeId;
    private Integer versionNo;
    private String status;
    private List<MaterialFieldDefinition> fields;
    private String schemaHash;
    private Long publishedByUserId;
    private LocalDateTime publishedAt;
    private Integer version;
}
