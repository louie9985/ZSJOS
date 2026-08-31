package cn.iocoder.yudao.module.system.controller.admin.ip.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Schema(description = "管理后台 - 地区列表 Request VO")
@Data
public class AreaListReqVO {

    @Schema(description = "地区名称")
    private String name;

    @Schema(description = "状态")
    private Integer status;

}
