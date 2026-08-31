package cn.iocoder.yudao.module.eam.controller.admin.category.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Schema(description = "管理后台 - EAM 资产分类创建/更新 Request VO")
@Data
public class EamCategorySaveReqVO {

    @Schema(description = "分类编号（更新时必填）", example = "1")
    private Long id;

    @Schema(description = "父分类编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "0")
    @NotNull(message = "父分类编号不能为空")
    private Long parentId;

    @Schema(description = "分类名称", requiredMode = Schema.RequiredMode.REQUIRED, example = "设备资产")
    @NotBlank(message = "分类名称不能为空")
    private String name;

    @Schema(description = "分类编码", requiredMode = Schema.RequiredMode.REQUIRED, example = "IT")
    @NotBlank(message = "分类编码不能为空")
    private String code;

    @Schema(description = "排序", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    @NotNull(message = "排序不能为空")
    private Integer sort;

    @Schema(description = "状态（0开启 1关闭）", requiredMode = Schema.RequiredMode.REQUIRED, example = "0")
    @NotNull(message = "状态不能为空")
    private Integer status;

    @Schema(description = "管理模式：1 单件，2 批量", example = "1")
    private Integer managementMode;

    @Schema(description = "交付模式：1 实物入库，2 数字交付；子分类为空表示继承")
    private Integer deliveryMode;

    @Schema(description = "持有模式：1 消耗型，2 需归还型；子分类为空表示继承")
    private Integer custodyMode;

    @Schema(description = "计量单位", example = "个")
    private String unit;

    @Schema(description = "备注", example = "IT设备")
    private String remark;

}
