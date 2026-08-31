package cn.iocoder.yudao.module.zsjos.controller.admin.workplan.vo;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class WorkPlanTemplateItemSaveReqVO {
    @NotBlank private String title;
    private String description;
    private String deliverableRequirement;
    private Integer dueOffsetDays;
    private String dueOffsetBasis;
    private Boolean confirmationRequired;
    private Integer sort;
}
