package cn.iocoder.yudao.module.eam.controller.admin.asset.vo;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

@Schema(description = "管理后台 - EAM 资产分页 Request VO")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class EamAssetPageReqVO extends PageParam {

    @Schema(description = "资产名称", example = "MacBook Pro")
    private String name;

    @Schema(description = "资产编号", example = "IT-2026-0001")
    private String assetCode;

    @Schema(description = "分类编号", example = "1")
    private Long categoryId;

    @Schema(description = "资产状态", example = "0")
    private Integer status;

    @Schema(description = "使用部门编号", example = "100")
    private Long useDeptId;

    @Schema(description = "使用人编号", example = "1")
    private Long useUserId;

    @Schema(description = "来源", example = "1")
    private Integer source;

}
