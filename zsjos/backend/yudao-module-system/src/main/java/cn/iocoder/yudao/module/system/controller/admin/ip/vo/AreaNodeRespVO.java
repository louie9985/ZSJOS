package cn.iocoder.yudao.module.system.controller.admin.ip.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

@Schema(description = "管理后台 - 地区节点 Response VO")
@Data
public class AreaNodeRespVO {

    @Schema(description = "编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "110000")
    private Integer id;

    @Schema(description = "名字", requiredMode = Schema.RequiredMode.REQUIRED, example = "北京")
    private String name;

    @Schema(description = "业务提交编码")
    private String selectionCode;

    @Schema(description = "地区类型")
    private Integer type;

    @Schema(description = "父地区编号")
    private Integer parentId;

    @Schema(description = "省级节点是否允许直接选择")
    private Boolean leafSelectable;

    /**
     * 子节点
     */
    private List<AreaNodeRespVO> children;

}
