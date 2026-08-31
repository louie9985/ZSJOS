package cn.iocoder.yudao.module.eam.controller.admin.scrap.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;

@Schema(description = "管理后台 - EAM 报废申请 Request VO")
@Data
public class EamScrapCreateReqVO {

    @Schema(description = "资产编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    @NotNull(message = "资产不能为空")
    private Long assetId;

    @Schema(description = "报废原因类型（字典 eam_scrap_reason）",
            requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    @NotNull(message = "报废原因类型不能为空")
    private Integer reasonType;

    @Schema(description = "详细原因", example = "主板损坏，维修成本超过残值")
    private String reason;

    @Schema(description = "报废日期，留空取当前日期", example = "2026-08-16")
    private LocalDate scrapDate;

}
