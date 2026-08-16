package cn.iocoder.yudao.module.eam.controller.admin.inventory.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Schema(description = "管理后台 - EAM 盘点单创建 Request VO")
@Data
public class EamInventoryCreateReqVO {

    @Schema(description = "盘点名称", requiredMode = Schema.RequiredMode.REQUIRED, example = "2026年Q3资产盘点")
    @NotBlank(message = "盘点名称不能为空")
    private String name;

    @Schema(description = "范围类型（1全部 2按部门 3按分类 4按存放地点）",
            requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    @NotNull(message = "范围类型不能为空")
    private Integer scopeType;

    @Schema(description = "范围值，多个用逗号分隔；范围类型为全部时留空", example = "100,101")
    private String scopeValue;

    @Schema(description = "备注", example = "季度例行盘点")
    private String remark;

}
