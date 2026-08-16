package cn.iocoder.yudao.module.eam.controller.admin.coderule.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Schema(description = "管理后台 - EAM 资产编号规则 Response VO")
@Data
public class EamCodeRuleRespVO {

    @Schema(description = "规则编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    private Long id;

    @Schema(description = "适用分类编号，空表示全局默认", example = "1")
    private Long categoryId;

    @Schema(description = "固定前缀", example = "IT")
    private String prefix;

    @Schema(description = "是否拼接分类编码", requiredMode = Schema.RequiredMode.REQUIRED, example = "true")
    private Boolean useCategoryCode;

    @Schema(description = "日期格式", example = "yyyy")
    private String dateFormat;

    @Schema(description = "流水号位数", requiredMode = Schema.RequiredMode.REQUIRED, example = "4")
    private Integer serialLength;

    @Schema(description = "分隔符", example = "-")
    private String separator;

    @Schema(description = "当前流水号", requiredMode = Schema.RequiredMode.REQUIRED, example = "12")
    private Long currentSerial;

}
