package cn.iocoder.yudao.module.eam.controller.admin.transfer.vo;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

@Schema(description = "管理后台 - EAM 流转单分页 Request VO")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class EamTransferPageReqVO extends PageParam {

    @Schema(description = "流转类型", example = "1")
    private Integer type;

    @Schema(description = "资产编号", example = "1")
    private Long assetId;

    @Schema(description = "单据状态", example = "1")
    private Integer status;

    @Schema(description = "单据编号", example = "TR-001")
    private String no;

}
