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
    ErrorCode CATEGORY_POLICY_REQUIRED = new ErrorCode(1_070_001_007, "根分类必须确认交付模式和持有模式");
    ErrorCode CATEGORY_POLICY_INVALID = new ErrorCode(1_070_001_008, "分类交付模式或持有模式不合法");
    ErrorCode CATEGORY_POLICY_UNCONFIRMED = new ErrorCode(1_070_001_009, "分类及其父分类尚未确认交付模式和持有模式");

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
    ErrorCode ASSET_IMPORT_HAS_ERRORS = new ErrorCode(1_070_003_006, "资产台账存在 {} 个错误，请先修正后再提交");
    ErrorCode ASSET_PUBLIC_CLEAR_USAGE_HOLDING_ACTIVE = new ErrorCode(1_070_003_007,
            "该资产存在未完成的员工领用或退还记录，请先完成员工资产退还流程");

    // ========== 流转 1-070-004-000 ==========
    ErrorCode TRANSFER_NOT_EXISTS = new ErrorCode(1_070_004_000, "流转单不存在");
    ErrorCode TRANSFER_STATUS_INVALID = new ErrorCode(1_070_004_001, "流转单当前状态不允许该操作");
    ErrorCode TRANSFER_TYPE_INVALID = new ErrorCode(1_070_004_002, "流转类型不合法");
    ErrorCode TRANSFER_RECEIVER_INVALID = new ErrorCode(1_070_004_003, "接收员工或部门无效");
    ErrorCode TRANSFER_CANDIDATE_EMPTY = new ErrorCode(1_070_004_004, "资产流转审批人配置不完整");
    ErrorCode TRANSFER_CANCEL_FORBIDDEN = new ErrorCode(1_070_004_005, "仅申请人可以取消流转单");
    ErrorCode TRANSFER_INSPECTION_RESULT_INVALID = new ErrorCode(1_070_004_006, "资产验收结果不合法");
    ErrorCode TRANSFER_PROCESS_UNAVAILABLE = new ErrorCode(1_070_004_007, "资产流转审批流程未部署或未启用");
    ErrorCode TRANSFER_INSPECTION_FORBIDDEN = new ErrorCode(1_070_004_008, "无权验收该资产流转单");

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

    // ========== 办公采购 1-070-009-000 ==========
    ErrorCode DEMAND_NOT_EXISTS = new ErrorCode(1_070_009_000, "资产需求不存在");
    ErrorCode DEMAND_STATUS_INVALID = new ErrorCode(1_070_009_001, "资产需求当前状态不允许该操作");
    ErrorCode DEMAND_ITEM_NOT_EXISTS = new ErrorCode(1_070_009_002, "资产需求明细不存在");
    ErrorCode PURCHASE_NOT_EXISTS = new ErrorCode(1_070_009_003, "办公采购单不存在");
    ErrorCode PURCHASE_STATUS_INVALID = new ErrorCode(1_070_009_004, "办公采购单当前状态不允许该操作");
    ErrorCode PURCHASE_ITEM_NOT_EXISTS = new ErrorCode(1_070_009_005, "办公采购明细不存在");
    ErrorCode PURCHASE_QUANTITY_INVALID = new ErrorCode(1_070_009_006, "采购、入库或退货数量超出可处理数量");
    ErrorCode STOCK_NOT_EXISTS = new ErrorCode(1_070_009_007, "库存品项不存在");
    ErrorCode STOCK_INSUFFICIENT = new ErrorCode(1_070_009_008, "可用库存不足，请刷新后重新确认");
    ErrorCode STOCK_CANDIDATE_INVALID = new ErrorCode(1_070_009_009, "库存候选与需求分类或属性不匹配");
    ErrorCode HOLDING_NOT_EXISTS = new ErrorCode(1_070_009_010, "员工资产持有记录不存在");
    ErrorCode HOLDING_STATUS_INVALID = new ErrorCode(1_070_009_011, "员工资产当前状态不允许该操作");
    ErrorCode RETURN_RESULT_INVALID = new ErrorCode(1_070_009_012, "资产退还验收结果不合法");
    ErrorCode EMPLOYEE_ASSET_TASK_NOT_EXISTS = new ErrorCode(1_070_009_013, "员工资产任务不存在");
    ErrorCode EMPLOYEE_NOT_BOUND = new ErrorCode(1_070_009_014, "员工尚未绑定系统账号，无法创建资产需求");
    ErrorCode EMPLOYEE_NOT_EXISTS = new ErrorCode(1_070_009_019, "HRM 员工不存在");
    ErrorCode PURCHASE_PAYMENT_MODE_INVALID = new ErrorCode(1_070_009_015, "付款方式未配置或已停用");
    ErrorCode PURCHASE_SERIAL_NUMBER_INVALID = new ErrorCode(1_070_009_016, "单件资产入库或退货必须填写与数量一致且不重复的序列号");
    ErrorCode EMPLOYEE_ASSET_TASK_STATUS_INVALID = new ErrorCode(1_070_009_017, "员工资产任务当前状态不允许该操作");
    ErrorCode PURCHASE_RETURN_SOURCE_INVALID = new ErrorCode(1_070_009_018, "退货物品不属于该采购入库批次、已退货或当前不可退");

}
