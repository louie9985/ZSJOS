package cn.iocoder.yudao.module.eam.controller.admin.coderule.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Schema(description = "管理后台 - EAM 资产编号规则创建/更新 Request VO")
@Data
public class EamCodeRuleSaveReqVO {

    @Schema(description = "规则编号（更新时必填）", example = "1")
    private Long id;

    @Schema(description = "适用分类编号，留空表示全局默认规则", example = "1")
    private Long categoryId;

    @Schema(description = "固定前缀", example = "IT")
    private String prefix;

    @Schema(description = "是否拼接分类编码", requiredMode = Schema.RequiredMode.REQUIRED, example = "true")
    @NotNull(message = "是否拼接分类编码不能为空")
    private Boolean useCategoryCode;

    @Schema(description = "日期格式（yyyy / yyyyMM），留空表示不含日期", example = "yyyy")
    private String dateFormat;

    @Schema(description = "流水号位数", requiredMode = Schema.RequiredMode.REQUIRED, example = "4")
    @NotNull(message = "流水号位数不能为空")
    @Min(value = 1, message = "流水号位数至少为 1")
    @Max(value = 12, message = "流水号位数最多为 12")
    private Integer serialLength;

    @Schema(description = "分隔符", example = "-")
    private String separator;

}
