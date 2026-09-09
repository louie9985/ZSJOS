package cn.iocoder.yudao.module.zsjos.controller.admin.material.vo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class MaterialTypeSaveReqVO {
    @NotBlank @Size(max = 100) private String name;
    @NotBlank @Pattern(regexp = "[a-z][a-z0-9_-]{1,63}") private String code;
    @Size(max = 500) private String description;
    @NotNull @Min(0) @Max(1) private Integer status;
    @NotNull private Boolean allowManualCreate;
    @NotNull private Boolean allowImport;
    @NotNull private Boolean allowAutoCollect;
    @NotNull private Boolean recommendationEnabled;
    private java.util.Map<String, Object> recommendationConfig;
    @Size(max = 128) private String bpmProcessDefinitionKey;
    private Integer version;
}
