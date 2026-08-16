package cn.iocoder.yudao.module.eam.controller.admin.inventory.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Schema(description = "管理后台 - EAM 盘点明细录入 Request VO")
@Data
public class EamInventoryCheckReqVO {

    @Schema(description = "盘点明细编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    @NotNull(message = "盘点明细不能为空")
    private Long detailId;

    @Schema(description = "盘点结果（1正常 2位置不符 3未找到）",
            requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    @NotNull(message = "盘点结果不能为空")
    private Integer result;

    @Schema(description = "实盘使用人编号", example = "2")
    private Long actualUserId;

    @Schema(description = "实盘使用部门编号", example = "101")
    private Long actualDeptId;

    @Schema(description = "实盘存放地点", example = "总部五楼")
    private String actualLocation;

    @Schema(description = "备注", example = "已挪至五楼会议室")
    private String remark;

}
