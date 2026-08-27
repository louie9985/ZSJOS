package cn.iocoder.yudao.module.eam.controller.admin.transfer.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;

@Schema(description = "管理后台 - EAM 流转单创建 Request VO")
@Data
public class EamTransferCreateReqVO {

    @Schema(description = "流转类型（1领用 2退还 3借用 4归还 5调拨）",
            requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    @NotNull(message = "流转类型不能为空")
    private Integer type;

    @Schema(description = "资产编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    @NotNull(message = "资产不能为空")
    private Long assetId;

    @Schema(description = "接收使用人编号（领用/借用/调拨必填）", example = "2")
    private Long toEmployeeId;

    @Schema(description = "接收部门编号（领用/借用/调拨必填）", example = "101")
    private Long toDeptId;

    @Schema(description = "预计归还日期（借用必填）", example = "2026-09-30")
    private LocalDate expectedReturnDate;

    @Schema(description = "实际归还日期（归还时填写）", example = "2026-09-20")
    private LocalDate actualReturnDate;

    @Schema(description = "事由", example = "新员工入职配发")
    private String reason;

}
