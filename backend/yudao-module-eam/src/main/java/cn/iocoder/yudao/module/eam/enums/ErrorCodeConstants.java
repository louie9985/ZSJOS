package cn.iocoder.yudao.module.eam.enums;

import cn.iocoder.yudao.framework.common.exception.ErrorCode;

/**
 * EAM 错误码枚举类
 *
 * EAM 系统，使用 1-070-000-000 段
 */
public interface ErrorCodeConstants {

    // ========== 资产分类 1-070-001-000 ==========
    ErrorCode CATEGORY_NOT_EXISTS = new ErrorCode(1_070_001_000, "资产分类不存在");
    ErrorCode CATEGORY_HAS_CHILDREN = new ErrorCode(1_070_001_001, "存在子分类，无法删除");
    ErrorCode CATEGORY_HAS_ASSET = new ErrorCode(1_070_001_002, "分类下存在资产，无法删除");
    ErrorCode CATEGORY_CODE_DUPLICATE = new ErrorCode(1_070_001_003, "分类编码已存在");
    ErrorCode CATEGORY_PARENT_ERROR = new ErrorCode(1_070_001_004, "不能设置自己或子分类为父分类");
    ErrorCode CATEGORY_IMPORT_FILE_INVALID = new ErrorCode(1_070_001_005, "分类配置文件无效：{}");
    ErrorCode CATEGORY_IMPORT_CONFLICT = new ErrorCode(1_070_001_006, "分类配置存在 {} 个冲突，请先修正模板");

    // ========== 分类字段 1-070-002-000 ==========
    ErrorCode FIELD_NOT_EXISTS = new ErrorCode(1_070_002_000, "自定义字段不存在");
    ErrorCode FIELD_KEY_DUPLICATE = new ErrorCode(1_070_002_001, "字段标识【{}】已存在");
    ErrorCode FIELD_REQUIRED = new ErrorCode(1_070_002_002, "字段【{}】为必填项");
    ErrorCode FIELD_VALUE_INVALID = new ErrorCode(1_070_002_003, "字段【{}】的值不合法");

    // ========== 资产 1-070-003-000 ==========
    ErrorCode ASSET_NOT_EXISTS = new ErrorCode(1_070_003_000, "资产不存在");
    ErrorCode ASSET_CODE_DUPLICATE = new ErrorCode(1_070_003_001, "资产编号已存在");
    ErrorCode ASSET_STATUS_INVALID = new ErrorCode(1_070_003_002, "资产【{}】当前状态不允许该操作");
    ErrorCode ASSET_IMPORT_LIST_EMPTY = new ErrorCode(1_070_003_003, "导入资产数据不能为空");
    ErrorCode ASSET_IMPORT_FILE_INVALID = new ErrorCode(1_070_003_004, "资产台账文件无效：{}");
    ErrorCode ASSET_IMPORT_CATEGORY_MISSING = new ErrorCode(1_070_003_005, "资产分类【{} / {}】不存在，请先导入分类配置");

    // ========== 流转 1-070-004-000 ==========
    ErrorCode TRANSFER_NOT_EXISTS = new ErrorCode(1_070_004_000, "流转单不存在");
    ErrorCode TRANSFER_STATUS_INVALID = new ErrorCode(1_070_004_001, "流转单当前状态不允许该操作");
    ErrorCode TRANSFER_TYPE_INVALID = new ErrorCode(1_070_004_002, "流转类型不合法");

    // ========== 盘点 1-070-005-000 ==========
    ErrorCode INVENTORY_NOT_EXISTS = new ErrorCode(1_070_005_000, "盘点单不存在");
    ErrorCode INVENTORY_FINISHED = new ErrorCode(1_070_005_001, "盘点单已完成，无法修改");
    ErrorCode INVENTORY_DETAIL_NOT_EXISTS = new ErrorCode(1_070_005_002, "盘点明细不存在");
    ErrorCode INVENTORY_SCOPE_EMPTY = new ErrorCode(1_070_005_003, "盘点范围内没有资产");

    // ========== 维修 1-070-006-000 ==========
    ErrorCode REPAIR_NOT_EXISTS = new ErrorCode(1_070_006_000, "维修记录不存在");
    ErrorCode REPAIR_FINISHED = new ErrorCode(1_070_006_001, "维修已完成，无法重复操作");

    // ========== 报废 1-070-007-000 ==========
    ErrorCode SCRAP_NOT_EXISTS = new ErrorCode(1_070_007_000, "报废单不存在");
    ErrorCode SCRAP_STATUS_INVALID = new ErrorCode(1_070_007_001, "报废单当前状态不允许该操作");

    // ========== 编号规则 1-070-008-000 ==========
    ErrorCode CODE_RULE_NOT_EXISTS = new ErrorCode(1_070_008_000, "资产编号规则不存在");
    ErrorCode CODE_RULE_GENERATE_FAIL = new ErrorCode(1_070_008_001, "资产编号生成失败");

}
