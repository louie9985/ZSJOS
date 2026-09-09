package cn.iocoder.yudao.module.zsjos.controller.admin.material.vo;

import cn.iocoder.yudao.module.zsjos.service.material.MaterialFieldDefinition;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class MaterialSchemaSaveReqVO {
    private Long id;
    private Integer version;
    @NotEmpty @Size(max = 100) @Valid private List<MaterialFieldDefinition> fields;
}
