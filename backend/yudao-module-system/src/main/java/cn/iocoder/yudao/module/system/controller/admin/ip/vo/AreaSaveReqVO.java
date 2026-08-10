package cn.iocoder.yudao.module.system.controller.admin.ip.vo;

import cn.iocoder.yudao.framework.common.enums.CommonStatusEnum;
import cn.iocoder.yudao.framework.common.validation.InEnum;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Schema(description = "管理后台 - 地区新增/修改 Request VO")
@Data
public class AreaSaveReqVO {

    @Schema(description = "稳定行政区编码；修改时不可变", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull
    @Min(1)
    private Integer id;

    @Schema(description = "地区名称", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank
    @Size(max = 100)
    private String name;

    @Schema(description = "父地区编码", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull
    private Integer parentId;

    @Schema(description = "显示顺序", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull
    @Min(0)
    private Integer sort;

    @Schema(description = "状态", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull
    @InEnum(CommonStatusEnum.class)
    private Integer status;

    @Schema(description = "省级节点是否允许直接选择")
    @NotNull
    private Boolean leafSelectable;

}
