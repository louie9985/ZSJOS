package cn.iocoder.yudao.module.infra.controller.admin.db.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

@Schema(description = "管理后台 - 数据库管理表详情 Response VO")
@Data
public class DatabaseAdminTableDetailRespVO extends DatabaseAdminTableRespVO {

    @Schema(description = "字段列表", requiredMode = Schema.RequiredMode.REQUIRED)
    private List<DatabaseAdminColumnRespVO> columns;

}
