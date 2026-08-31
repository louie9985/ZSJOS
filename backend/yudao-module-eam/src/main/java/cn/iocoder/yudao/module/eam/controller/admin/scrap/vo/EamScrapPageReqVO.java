package cn.iocoder.yudao.module.eam.controller.admin.scrap.vo;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

@Schema(description = "管理后台 - EAM 报废单分页 Request VO")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class EamScrapPageReqVO extends PageParam {

    @Schema(description = "资产编号", example = "1")
    private Long assetId;

    @Schema(description = "状态", example = "0")
    private Integer status;

    @Schema(description = "单据编号", example = "SC-001")
    private String no;

}
