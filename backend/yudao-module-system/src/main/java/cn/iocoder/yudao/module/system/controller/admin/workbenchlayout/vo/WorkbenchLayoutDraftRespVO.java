package cn.iocoder.yudao.module.system.controller.admin.workbenchlayout.vo;

import cn.iocoder.yudao.module.system.service.workbenchlayout.model.WorkbenchLayoutSnapshot;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorkbenchLayoutDraftRespVO {

    private String scopeType;
    private Long scopeId;
    private Integer draftRevision;
    private WorkbenchLayoutSnapshot snapshot;
    private Long publishedVersionId;
    private Integer publishedVersionNo;
    private Boolean publishedEnabled;
    private Integer publishedPriority;

}
