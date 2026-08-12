package cn.iocoder.yudao.module.zsjos.controller.admin.workplan.vo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class WorkPlanTypeSaveReqVO {
    @Size(max = 64) private String code;
    @NotBlank @Size(max = 100) private String name;
    @Size(max = 500) private String description;
    private Integer sort;
}
