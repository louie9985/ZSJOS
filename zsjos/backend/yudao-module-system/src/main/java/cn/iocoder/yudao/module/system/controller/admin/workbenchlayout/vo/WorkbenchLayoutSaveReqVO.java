package cn.iocoder.yudao.module.system.controller.admin.workbenchlayout.vo;

import cn.iocoder.yudao.module.system.service.workbenchlayout.model.WorkbenchLayoutSnapshot;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class WorkbenchLayoutSaveReqVO {

    @NotBlank
    private String scopeType;
    @NotNull
    private Long scopeId;
    @NotNull
    @Min(0)
    private Integer draftRevision;
    @NotNull
    private WorkbenchLayoutSnapshot snapshot;

}
