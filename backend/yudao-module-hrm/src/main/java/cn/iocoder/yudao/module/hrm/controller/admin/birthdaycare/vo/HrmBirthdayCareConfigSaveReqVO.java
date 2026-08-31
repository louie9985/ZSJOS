package cn.iocoder.yudao.module.hrm.controller.admin.birthdaycare.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

@Data
public class HrmBirthdayCareConfigSaveReqVO {
    @NotNull
    private Boolean enabled;
    @Min(0)
    @Max(30)
    private Integer advanceDays;
    @NotBlank
    @Schema(description = "触发时间，HH:mm")
    private String triggerTime;
    private List<Long> deptIds;
    private Boolean includeChildDepartments;
}
