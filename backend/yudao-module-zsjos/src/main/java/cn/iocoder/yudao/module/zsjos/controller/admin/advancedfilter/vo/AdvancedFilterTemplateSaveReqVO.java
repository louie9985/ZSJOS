package cn.iocoder.yudao.module.zsjos.controller.admin.advancedfilter.vo;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class AdvancedFilterTemplateSaveReqVO {
    private Long id;

    @NotBlank(message = "筛选场景不能为空")
    @Pattern(regexp = "lead|order|lead_appeal|duplicate_review|registration|student|subordinate_sales",
            message = "筛选场景无效")
    private String scene;

    @NotBlank(message = "页面标识不能为空")
    @Pattern(regexp = "[a-z][a-z0-9_:-]{1,95}", message = "页面标识格式不正确")
    private String pageKey;

    @NotBlank(message = "模板名称不能为空")
    @Size(max = 30, message = "模板名称不能超过 30 个字符")
    private String name;

    @Valid
    @NotNull(message = "筛选条件不能为空")
    private AdvancedFilterGroupReqVO filter;

    @NotNull(message = "排序不能为空")
    private Integer sort;

    @NotNull(message = "启用状态不能为空")
    private Boolean enabled;

    @NotNull(message = "默认状态不能为空")
    private Boolean defaultTemplate;

    private Integer version;
}
