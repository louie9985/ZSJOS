package cn.iocoder.yudao.module.zsjos.controller.admin.workorder.vo;

import cn.iocoder.yudao.module.zsjos.service.workorder.WorkOrderFieldDefinition;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class WorkOrderSceneCreateReqVO {
    @NotBlank @Size(max = 64) private String code;
    @NotBlank @Size(max = 128) private String name;
    @Size(max = 500) private String remark;
    @NotBlank @Size(max = 64) private String sourcePostCode;
    @NotBlank @Size(max = 64) private String targetPostCode;
    @NotBlank @Size(max = 32) private String assignmentMode;
    @NotEmpty @Size(max = 100) private List<WorkOrderFieldDefinition> fields;
    @NotNull @Min(0) @Max(1) private Integer status;
}
