package cn.iocoder.yudao.module.zsjos.controller.admin.materialimport.vo;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class MaterialImportCommitReqVO {
    @NotNull
    private Integer expectedVersion;
}
