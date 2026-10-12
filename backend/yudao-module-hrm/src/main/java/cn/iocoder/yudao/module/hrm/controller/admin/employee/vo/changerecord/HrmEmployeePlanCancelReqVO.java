package cn.iocoder.yudao.module.hrm.controller.admin.employee.vo.changerecord;

import jakarta.validation.constraints.*;
import lombok.Data;

@Data
public class HrmEmployeePlanCancelReqVO {
    @NotNull private Long id;
    @NotNull private Long employeeId;
    @NotNull private Long version;
    @NotNull private Long employeeVersion;
    @NotBlank @Size(max = 64) private String operationKey;
    @NotBlank @Size(max = 500) private String reason;
}
