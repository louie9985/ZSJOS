package cn.iocoder.yudao.module.infra.controller.admin.db.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Schema(description = "管理后台 - 数据库管理表 Response VO")
@Data
public class DatabaseAdminTableRespVO {

    @Schema(description = "表名", requiredMode = Schema.RequiredMode.REQUIRED, example = "system_users")
    private String name;

    @Schema(description = "表注释", example = "用户表")
    private String remarks;

    @Schema(description = "单列主键字段；为空表示不支持写操作", example = "id")
    private String primaryKeyColumn;

    @Schema(description = "是否支持新增、编辑、删除", requiredMode = Schema.RequiredMode.REQUIRED, example = "true")
    private Boolean writable;

}
