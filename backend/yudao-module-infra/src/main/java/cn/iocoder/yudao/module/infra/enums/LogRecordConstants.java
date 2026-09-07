package cn.iocoder.yudao.module.infra.enums;

/**
 * Infra 操作日志枚举。
 */
public interface LogRecordConstants {

    String INFRA_DATABASE_ADMIN_TYPE = "INFRA 数据库管理";
    String INFRA_DATABASE_ADMIN_CREATE_SUB_TYPE = "新增数据库行";
    String INFRA_DATABASE_ADMIN_CREATE_SUCCESS = "在数据源【{{#reqVO.dataSourceConfigId}}】表【{{#reqVO.tableName}}】新增字段【{{#reqVO.values.keySet()}}】";
    String INFRA_DATABASE_ADMIN_UPDATE_SUB_TYPE = "更新数据库行";
    String INFRA_DATABASE_ADMIN_UPDATE_SUCCESS = "在数据源【{{#reqVO.dataSourceConfigId}}】表【{{#reqVO.tableName}}】更新字段【{{#reqVO.values.keySet()}}】";
    String INFRA_DATABASE_ADMIN_DELETE_SUB_TYPE = "删除数据库行";
    String INFRA_DATABASE_ADMIN_DELETE_SUCCESS = "在数据源【{{#reqVO.dataSourceConfigId}}】表【{{#reqVO.tableName}}】删除数据行";

}
