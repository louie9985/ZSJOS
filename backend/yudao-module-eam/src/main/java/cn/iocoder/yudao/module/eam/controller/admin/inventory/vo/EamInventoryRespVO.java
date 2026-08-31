package cn.iocoder.yudao.module.eam.controller.admin.inventory.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Schema(description = "管理后台 - EAM 盘点单 Response VO")
@Data
public class EamInventoryRespVO {

    @Schema(description = "盘点单编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    private Long id;

    @Schema(description = "盘点单号", requiredMode = Schema.RequiredMode.REQUIRED, example = "IV-2026-0001")
    private String no;

    @Schema(description = "盘点名称", requiredMode = Schema.RequiredMode.REQUIRED, example = "2026年Q3资产盘点")
    private String name;

    @Schema(description = "范围类型", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    private Integer scopeType;

    @Schema(description = "范围值", example = "100,101")
    private String scopeValue;

    @Schema(description = "盘点状态（0进行中 1已完成）",
            requiredMode = Schema.RequiredMode.REQUIRED, example = "0")
    private Integer status;

    @Schema(description = "应盘数量", requiredMode = Schema.RequiredMode.REQUIRED, example = "120")
    private Integer totalCount;

    @Schema(description = "已盘数量", requiredMode = Schema.RequiredMode.REQUIRED, example = "118")
    private Integer checkedCount;

    @Schema(description = "正常数量", requiredMode = Schema.RequiredMode.REQUIRED, example = "115")
    private Integer normalCount;

    @Schema(description = "异常数量", requiredMode = Schema.RequiredMode.REQUIRED, example = "3")
    private Integer abnormalCount;

    @Schema(description = "开始时间", requiredMode = Schema.RequiredMode.REQUIRED)
    private LocalDateTime startTime;

    @Schema(description = "结束时间")
    private LocalDateTime endTime;

    @Schema(description = "备注", example = "季度例行盘点")
    private String remark;

}
