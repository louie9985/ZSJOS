package cn.iocoder.yudao.module.zsjos.controller.admin.workplan.vo;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class WorkPlanTemplateFieldSaveReqVO {
    private Long id;
    private String fieldKey;
    @NotBlank private String label;
    @NotBlank private String section;
    @NotBlank private String fieldType;
    private Boolean required;
    private String unit;
    private String placeholder;
    private Boolean filterable;
    private Boolean exportable;
    private String optionsJson;
    private String defaultValueJson;
    private Integer sort;
}
