package cn.iocoder.yudao.module.zsjos.controller.admin.material.vo;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class MaterialTemplatePublishReqVO {
    @NotNull private Long schemaVersionId;
    @NotNull private Integer expectedSchemaVersion;
    @NotNull private Integer expectedTypeVersion;
}
