package cn.iocoder.yudao.module.infra.controller.admin.db.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Schema(description = "管理后台 - 数据库管理表数据 Response VO")
@Data
public class DatabaseAdminTableDataRespVO {

    @Schema(description = "表详情", requiredMode = Schema.RequiredMode.REQUIRED)
    private DatabaseAdminTableDetailRespVO table;

    @Schema(description = "总条数", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long total;

    @Schema(description = "行数据", requiredMode = Schema.RequiredMode.REQUIRED)
    private List<Map<String, Object>> rows;

}
