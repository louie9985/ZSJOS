package cn.iocoder.yudao.module.zsjos.controller.admin.workplan.vo;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class WorkPlanDecompositionReqVO {
    @NotNull private Integer version;
}
