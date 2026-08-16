package cn.iocoder.yudao.module.eam.controller.admin.repair.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Schema(description = "管理后台 - EAM 送修 Request VO")
@Data
public class EamRepairCreateReqVO {

    @Schema(description = "资产编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    @NotNull(message = "资产不能为空")
    private Long assetId;

    @Schema(description = "故障描述", requiredMode = Schema.RequiredMode.REQUIRED, example = "屏幕不亮")
    @NotBlank(message = "故障描述不能为空")
    private String faultDesc;

    @Schema(description = "维修方", example = "Apple 授权服务中心")
    private String repairVendor;

    @Schema(description = "维修费用", example = "800.00")
    private BigDecimal cost;

    @Schema(description = "送修时间，留空取当前时间", example = "2026-08-16T10:00:00")
    private LocalDateTime startTime;

}
