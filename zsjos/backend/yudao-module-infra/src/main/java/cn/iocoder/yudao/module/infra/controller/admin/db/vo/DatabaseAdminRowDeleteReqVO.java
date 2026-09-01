package cn.iocoder.yudao.module.infra.controller.admin.db.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Schema(description = "管理后台 - 数据库管理删除行 Request VO")
@Data
public class DatabaseAdminRowDeleteReqVO {

    @Schema(description = "数据源配置编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "0")
    @NotNull(message = "数据源配置编号不能为空")
    private Long dataSourceConfigId;

    @Schema(description = "表名", requiredMode = Schema.RequiredMode.REQUIRED, example = "system_users")
    @NotBlank(message = "表名不能为空")
    private String tableName;

    @Schema(description = "主键值", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    @NotNull(message = "主键值不能为空")
    private Object primaryKeyValue;

}
