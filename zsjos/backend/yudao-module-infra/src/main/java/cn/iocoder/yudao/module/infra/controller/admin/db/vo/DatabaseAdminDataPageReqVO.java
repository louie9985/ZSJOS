package cn.iocoder.yudao.module.infra.controller.admin.db.vo;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

@Schema(description = "管理后台 - 数据库管理表数据分页 Request VO")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class DatabaseAdminDataPageReqVO extends PageParam {

    @Schema(description = "数据源配置编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "0")
    @NotNull(message = "数据源配置编号不能为空")
    private Long dataSourceConfigId;

    @Schema(description = "表名", requiredMode = Schema.RequiredMode.REQUIRED, example = "system_users")
    @NotBlank(message = "表名不能为空")
    private String tableName;

    @Schema(description = "关键字", example = "admin")
    private String keyword;

}
