package cn.iocoder.yudao.module.eam.controller.admin.repair.vo;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

@Schema(description = "管理后台 - EAM 维修记录分页 Request VO")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class EamRepairPageReqVO extends PageParam {

    @Schema(description = "资产编号", example = "1")
    private Long assetId;

}
