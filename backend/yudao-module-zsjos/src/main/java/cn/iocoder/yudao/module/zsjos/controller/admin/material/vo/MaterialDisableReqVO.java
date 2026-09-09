package cn.iocoder.yudao.module.zsjos.controller.admin.material.vo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class MaterialDisableReqVO {
    @NotNull private Integer expectedVersion;
    @NotBlank @Size(max = 500) private String reason;
}
