package cn.iocoder.yudao.module.system.controller.admin.workbenchlayout.vo;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class WorkbenchLayoutPublishReqVO {

    @NotBlank
    private String scopeType;
    @NotNull
    private Long scopeId;
    @NotNull
    @Min(0)
    private Integer draftRevision;
    @NotBlank
    @Size(max = 500)
    private String publishRemark;

}
