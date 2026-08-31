package cn.iocoder.yudao.module.eam.controller.admin.scrap.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Schema(description = "管理后台 - EAM 报废单 Response VO")
@Data
public class EamScrapRespVO {

    @Schema(description = "单据编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    private Long id;

    @Schema(description = "单据业务编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "SC-2026-0001")
    private String no;

    @Schema(description = "资产编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    private Long assetId;

    @Schema(description = "资产名称", example = "MacBook Pro 14")
    private String assetName;

    @Schema(description = "资产业务编号", example = "IT-2026-0001")
    private String assetCode;

    @Schema(description = "报废原因类型", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    private Integer reasonType;

    @Schema(description = "详细原因", example = "主板损坏")
    private String reason;

    @Schema(description = "报废日期", example = "2026-08-16")
    private LocalDate scrapDate;

    @Schema(description = "状态（0审批中 1已报废 2已驳回）",
            requiredMode = Schema.RequiredMode.REQUIRED, example = "0")
    private Integer status;

    @Schema(description = "BPM 流程实例编号", example = "a1b2c3")
    private String processInstanceId;

    @Schema(description = "申请人编号", example = "1")
    private Long applyUserId;

    @Schema(description = "申请人名称", example = "管理员")
    private String applyUserName;

    @Schema(description = "申请时间", requiredMode = Schema.RequiredMode.REQUIRED)
    private LocalDateTime applyTime;

}
