package cn.iocoder.yudao.module.zsjos.controller.admin.workplan.vo;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

@Data
public class WorkPlanTemplateSaveReqVO {
    @NotNull private Long typeId;
    private String code;
    @NotBlank private String name;
    private String description;
    @NotBlank private String periodMode;
    private List<@Valid WorkPlanTemplateFieldSaveReqVO> fields;
    private List<Long> applicableDeptIds;
    private Boolean includeChildDepartments;
    private List<@Valid WorkPlanTemplateItemSaveReqVO> presetItems;
}
