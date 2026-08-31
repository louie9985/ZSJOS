package cn.iocoder.yudao.module.eam.controller.admin.repair.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Schema(description = "管理后台 - EAM 维修记录 Response VO")
@Data
public class EamRepairRespVO {

    @Schema(description = "维修记录编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    private Long id;

    @Schema(description = "资产编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    private Long assetId;

    @Schema(description = "资产名称", example = "MacBook Pro 14")
    private String assetName;

    @Schema(description = "资产业务编号", example = "IT-2026-0001")
    private String assetCode;

    @Schema(description = "故障描述", requiredMode = Schema.RequiredMode.REQUIRED, example = "屏幕不亮")
    private String faultDesc;

    @Schema(description = "维修方", example = "Apple 授权服务中心")
    private String repairVendor;

    @Schema(description = "维修费用", example = "800.00")
    private BigDecimal cost;

    @Schema(description = "送修时间", requiredMode = Schema.RequiredMode.REQUIRED)
    private LocalDateTime startTime;

    @Schema(description = "完成时间，空表示维修中")
    private LocalDateTime endTime;

    @Schema(description = "维修结果", example = "已更换屏幕")
    private String result;

}
