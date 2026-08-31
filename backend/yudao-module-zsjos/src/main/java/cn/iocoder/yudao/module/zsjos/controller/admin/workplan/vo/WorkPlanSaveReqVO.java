package cn.iocoder.yudao.module.zsjos.controller.admin.workplan.vo;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Data
public class WorkPlanSaveReqVO {
    @NotBlank @Size(max = 200) private String title;
    @NotBlank private String periodType;
    @NotNull private Long templateVersionId;
    @NotNull private Long ownerUserId;
    @Size(max = 4000) private String objective;
    @Size(max = 4000) private String keyRequirements;
    @NotNull private LocalDate startDate;
    @NotNull private LocalDate endDate;
    private Integer version;
    private String reason;
    @Valid private List<WorkPlanTemplateFieldSaveReqVO> supplementalFields;
    private Map<String, Object> planFields;
    @Valid private List<WorkTaskSaveReqVO> tasks;
}
