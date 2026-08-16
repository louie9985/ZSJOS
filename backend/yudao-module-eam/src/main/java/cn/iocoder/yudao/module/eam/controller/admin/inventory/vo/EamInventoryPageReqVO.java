package cn.iocoder.yudao.module.eam.controller.admin.inventory.vo;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

@Schema(description = "管理后台 - EAM 盘点单分页 Request VO")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class EamInventoryPageReqVO extends PageParam {

    @Schema(description = "盘点名称", example = "2026年Q3盘点")
    private String name;

    @Schema(description = "盘点状态", example = "0")
    private Integer status;

}
