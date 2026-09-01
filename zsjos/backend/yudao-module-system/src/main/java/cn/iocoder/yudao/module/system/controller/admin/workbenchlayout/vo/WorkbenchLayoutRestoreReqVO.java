package cn.iocoder.yudao.module.system.controller.admin.workbenchlayout.vo;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class WorkbenchLayoutRestoreReqVO {

    @NotBlank
    private String scopeType;
    @NotNull
    private Long scopeId;
    @NotNull
    private Long versionId;
    @NotNull
    @Min(0)
    private Integer draftRevision;

}
