package cn.iocoder.yudao.module.eam.controller.admin.category.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Schema(description = "管理后台 - EAM 分类自定义字段 Response VO")
@Data
public class EamCategoryFieldRespVO {

    @Schema(description = "字段编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    private Long id;

    @Schema(description = "所属分类编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    private Long categoryId;

    @Schema(description = "字段标识", requiredMode = Schema.RequiredMode.REQUIRED, example = "account")
    private String fieldKey;

    @Schema(description = "字段显示名", requiredMode = Schema.RequiredMode.REQUIRED, example = "账号")
    private String fieldName;

    @Schema(description = "字段类型", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    private Integer fieldType;

    @Schema(description = "下拉选项", example = "[\"移动\",\"联通\"]")
    private List<String> options;

    @Schema(description = "选项来源：STATIC 或 SYSTEM_DICT")
    private String optionSource;

    @Schema(description = "System 字典类型编码")
    private String dictType;

    @Schema(description = "是否必填", requiredMode = Schema.RequiredMode.REQUIRED, example = "false")
    private Boolean required;

    @Schema(description = "管理端是否显示", example = "true")
    private Boolean adminVisible;

    @Schema(description = "员工收集表是否显示", example = "true")
    private Boolean collectionVisible;

    @Schema(description = "员工收集表是否必填", example = "false")
    private Boolean collectionRequired;

    @Schema(description = "员工收集表条件规则")
    private Map<String, Object> conditionRule;

    @Schema(description = "排序", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    private Integer sort;

    @Schema(description = "是否继承自父分类（继承字段不可在当前分类编辑）", example = "false")
    private Boolean inherited;

}
