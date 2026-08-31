package cn.iocoder.yudao.module.system.controller.admin.workbenchlayout.vo;

import cn.iocoder.yudao.module.system.service.workbenchlayout.model.WorkbenchLayoutSnapshot;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class WorkbenchLayoutPreviewReqVO {

    @NotNull
    private Long userId;
    private String scopeType;
    private Long scopeId;
    private WorkbenchLayoutSnapshot snapshot;

}
