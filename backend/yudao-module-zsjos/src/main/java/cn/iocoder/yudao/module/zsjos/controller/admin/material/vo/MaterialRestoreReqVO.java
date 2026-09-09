package cn.iocoder.yudao.module.zsjos.controller.admin.material.vo;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class MaterialRestoreReqVO {
    @NotNull
    private Integer expectedVersion;
}
