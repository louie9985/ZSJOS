package cn.iocoder.yudao.module.zsjos.controller.admin.workplan.vo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class WorkPlanDynamicFilterReqVO {
    @NotBlank private String fieldKey;
    @NotBlank private String operator;
    @NotNull private Object value;
}
