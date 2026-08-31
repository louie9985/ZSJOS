package cn.iocoder.yudao.module.infra.controller.admin.db.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Schema(description = "管理后台 - 数据库管理字段 Response VO")
@Data
public class DatabaseAdminColumnRespVO {

    @Schema(description = "字段名", requiredMode = Schema.RequiredMode.REQUIRED, example = "id")
    private String name;

    @Schema(description = "字段类型名", requiredMode = Schema.RequiredMode.REQUIRED, example = "BIGINT")
    private String typeName;

    @Schema(description = "JDBC 字段类型", requiredMode = Schema.RequiredMode.REQUIRED, example = "-5")
    private Integer jdbcType;

    @Schema(description = "字段注释", example = "编号")
    private String remarks;

    @Schema(description = "是否允许为空", requiredMode = Schema.RequiredMode.REQUIRED, example = "true")
    private Boolean nullable;

    @Schema(description = "是否主键", requiredMode = Schema.RequiredMode.REQUIRED, example = "true")
    private Boolean primaryKey;

    @Schema(description = "是否自增", requiredMode = Schema.RequiredMode.REQUIRED, example = "true")
    private Boolean autoIncrement;

    @Schema(description = "是否敏感字段", requiredMode = Schema.RequiredMode.REQUIRED, example = "false")
    private Boolean sensitive;

    @Schema(description = "是否可编辑", requiredMode = Schema.RequiredMode.REQUIRED, example = "true")
    private Boolean editable;

}
