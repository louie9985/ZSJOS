package cn.iocoder.yudao.module.system.controller.admin.ip.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Schema(description = "管理后台 - 地区 Response VO")
@Data
public class AreaRespVO {

    private Integer id;
    private String name;
    private String selectionCode;
    private Integer type;
    private Integer parentId;
    private Integer sort;
    private Integer status;
    private Boolean leafSelectable;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

}
