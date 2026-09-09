package cn.iocoder.yudao.module.zsjos.controller.admin.material.vo;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class MaterialReferencePreviewReqVO {
    @NotNull
    private Long targetContentVersionId;
    @NotEmpty
    @Size(max = 100)
    @Valid
    private List<MaterialReferenceFieldReqVO> fields;
}
