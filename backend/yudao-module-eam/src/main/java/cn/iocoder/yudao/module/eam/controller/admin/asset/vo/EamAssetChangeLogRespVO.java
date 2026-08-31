package cn.iocoder.yudao.module.eam.controller.admin.asset.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Schema(description = "管理后台 - EAM 资产变更记录 Response VO")
@Data
public class EamAssetChangeLogRespVO {

    @Schema(description = "记录编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    private Long id;

    @Schema(description = "资产编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    private Long assetId;

    @Schema(description = "变更类型", requiredMode = Schema.RequiredMode.REQUIRED, example = "2")
    private Integer changeType;

    @Schema(description = "变更前状态", example = "0")
    private Integer beforeStatus;

    @Schema(description = "变更后状态", example = "1")
    private Integer afterStatus;

    @Schema(description = "变更前使用人", example = "1")
    private Long beforeEmployeeId;

    @Schema(description = "变更后使用人", example = "2")
    private Long afterEmployeeId;

    @Schema(description = "变更前使用部门", example = "100")
    private Long beforeDeptId;

    @Schema(description = "变更后使用部门", example = "101")
    private Long afterDeptId;

    @Schema(description = "关联单据编号", example = "1")
    private Long bizId;

    @Schema(description = "变更描述", example = "领用给张三")
    private String content;

    @Schema(description = "操作人编号", example = "1")
    private Long operatorId;

    @Schema(description = "操作人名称", example = "管理员")
    private String operatorName;

    @Schema(description = "操作时间", requiredMode = Schema.RequiredMode.REQUIRED)
    private LocalDateTime operateTime;

}
