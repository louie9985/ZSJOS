package cn.iocoder.yudao.module.zsjos.controller.admin.workplan.vo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class WorkPlanCancelReqVO {
    @NotNull private Integer version;
    @NotBlank @Size(max = 500) private String reason;
    private Boolean cascadeChildren;
}
