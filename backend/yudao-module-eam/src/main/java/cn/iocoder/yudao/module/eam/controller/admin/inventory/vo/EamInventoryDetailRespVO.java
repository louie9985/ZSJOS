package cn.iocoder.yudao.module.eam.controller.admin.inventory.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Schema(description = "管理后台 - EAM 盘点明细 Response VO")
@Data
public class EamInventoryDetailRespVO {

    @Schema(description = "明细编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    private Long id;

    @Schema(description = "盘点单编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    private Long inventoryId;

    @Schema(description = "资产编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    private Long assetId;

    @Schema(description = "资产名称", example = "MacBook Pro 14")
    private String assetName;

    @Schema(description = "资产业务编号", example = "IT-2026-0001")
    private String assetCode;

    @Schema(description = "账面使用人编号", example = "1")
    private Long expectUserId;

    @Schema(description = "账面使用人名称", example = "张三")
    private String expectUserName;

    @Schema(description = "账面使用部门编号", example = "100")
    private Long expectDeptId;

    @Schema(description = "账面存放地点", example = "总部三楼")
    private String expectLocation;

    @Schema(description = "实盘使用人编号", example = "2")
    private Long actualUserId;

    @Schema(description = "实盘使用部门编号", example = "101")
    private Long actualDeptId;

    @Schema(description = "实盘存放地点", example = "总部五楼")
    private String actualLocation;

    @Schema(description = "盘点结果（0未盘 1正常 2位置不符 3未找到）",
            requiredMode = Schema.RequiredMode.REQUIRED, example = "0")
    private Integer result;

    @Schema(description = "备注", example = "已挪至五楼")
    private String remark;

    @Schema(description = "盘点人编号", example = "1")
    private Long checkUserId;

    @Schema(description = "盘点时间")
    private LocalDateTime checkTime;

}
