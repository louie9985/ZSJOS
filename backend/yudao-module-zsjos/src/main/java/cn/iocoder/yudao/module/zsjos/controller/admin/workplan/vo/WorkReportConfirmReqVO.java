package cn.iocoder.yudao.module.zsjos.controller.admin.workplan.vo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class WorkReportConfirmReqVO {
    @NotNull private Integer version;
    @NotBlank private String decision;
    @Size(max = 1000) private String comment;
}
