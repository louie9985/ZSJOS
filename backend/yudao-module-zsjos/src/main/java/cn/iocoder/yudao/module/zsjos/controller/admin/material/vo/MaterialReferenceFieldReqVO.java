package cn.iocoder.yudao.module.zsjos.controller.admin.material.vo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class MaterialReferenceFieldReqVO {
    @NotBlank private String sourceField;
    @NotBlank private String targetField;
    @NotBlank @Pattern(regexp = "REPLACE|APPEND|SKIP") private String action;
}
