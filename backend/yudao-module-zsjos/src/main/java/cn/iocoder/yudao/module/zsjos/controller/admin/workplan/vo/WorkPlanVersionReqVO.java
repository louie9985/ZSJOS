package cn.iocoder.yudao.module.zsjos.controller.admin.workplan.vo;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class WorkPlanVersionReqVO {
    @NotNull private Integer version;
}
