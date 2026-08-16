package cn.iocoder.yudao.module.eam.controller.admin.category.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

@Schema(description = "管理后台 - EAM 分类自定义字段创建/更新 Request VO")
@Data
public class EamCategoryFieldSaveReqVO {

    @Schema(description = "字段编号（更新时必填）", example = "1")
    private Long id;

    @Schema(description = "所属分类编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    @NotNull(message = "所属分类不能为空")
    private Long categoryId;

    @Schema(description = "字段标识", requiredMode = Schema.RequiredMode.REQUIRED, example = "account")
    @NotBlank(message = "字段标识不能为空")
    private String fieldKey;

    @Schema(description = "字段显示名", requiredMode = Schema.RequiredMode.REQUIRED, example = "账号")
    @NotBlank(message = "字段显示名不能为空")
    private String fieldName;

    @Schema(description = "字段类型（1单行文本 2多行文本 3数字 4日期 5下拉选择）",
            requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    @NotNull(message = "字段类型不能为空")
    private Integer fieldType;

    @Schema(description = "下拉选项，仅字段类型为下拉选择时使用", example = "[\"移动\",\"联通\"]")
    private List<String> options;

    @Schema(description = "是否必填", requiredMode = Schema.RequiredMode.REQUIRED, example = "false")
    @NotNull(message = "是否必填不能为空")
    private Boolean required;

    @Schema(description = "排序", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    @NotNull(message = "排序不能为空")
    private Integer sort;

}
