package cn.iocoder.yudao.module.eam.controller.admin.transfer.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Schema(description = "管理后台 - EAM 流转单 Response VO")
@Data
public class EamTransferRespVO {

    @Schema(description = "单据编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    private Long id;

    @Schema(description = "单据业务编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "TR-2026-0001")
    private String no;

    @Schema(description = "流转类型", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    private Integer type;

    @Schema(description = "资产编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    private Long assetId;

    @Schema(description = "资产名称", example = "MacBook Pro 14")
    private String assetName;

    @Schema(description = "资产业务编号", example = "IT-2026-0001")
    private String assetCode;

    @Schema(description = "转出使用人编号", example = "1")
    private Long fromEmployeeId;

    @Schema(description = "转出使用人名称", example = "张三")
    private String fromEmployeeName;

    @Schema(description = "转出部门编号", example = "100")
    private Long fromDeptId;

    @Schema(description = "接收使用人编号", example = "2")
    private Long toEmployeeId;

    @Schema(description = "接收使用人名称", example = "李四")
    private String toEmployeeName;

    @Schema(description = "接收部门编号", example = "101")
    private Long toDeptId;

    @Schema(description = "预计归还日期", example = "2026-09-30")
    private LocalDate expectedReturnDate;

    @Schema(description = "实际归还日期", example = "2026-09-20")
    private LocalDate actualReturnDate;

    @Schema(description = "单据状态", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    private Integer status;

    @Schema(description = "BPM 流程实例编号", example = "a1b2c3")
    private String processInstanceId;

    @Schema(description = "事由", example = "新员工入职配发")
    private String reason;

    @Schema(description = "申请人编号", example = "1")
    private Long applyUserId;

    @Schema(description = "申请人名称", example = "管理员")
    private String applyUserName;

    @Schema(description = "申请时间", requiredMode = Schema.RequiredMode.REQUIRED)
    private LocalDateTime applyTime;

}
