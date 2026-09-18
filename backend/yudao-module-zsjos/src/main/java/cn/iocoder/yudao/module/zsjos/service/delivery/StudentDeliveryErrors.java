package cn.iocoder.yudao.module.zsjos.service.delivery;

import cn.iocoder.yudao.framework.common.exception.ErrorCode;

interface StudentDeliveryErrors {
    ErrorCode SOURCE_REQUIRED = new ErrorCode(1_900_091_007, "等待定位卡生效或本期交付约定未填写");
    ErrorCode CYCLE_INVALID = new ErrorCode(1_900_091_008, "当前交付轮次不支持该操作，请刷新");
    ErrorCode DEFER_DAYS_INVALID = new ErrorCode(1_900_091_001, "延期天数必须为正整数且截止日期有效");
    ErrorCode DEFER_STAGE_INVALID = new ErrorCode(1_900_091_002, "当前阶段不可申请延期，请刷新交付计划");
    ErrorCode DEFER_PERMISSION_DENIED = new ErrorCode(1_900_091_003, "只有当前阶段责任编导可以申请延期");
    ErrorCode DEFER_SUPERVISOR_INVALID = new ErrorCode(1_900_091_004, "延期配置暂不可用，请联系管理员");
    ErrorCode DEFER_REASON_INVALID = new ErrorCode(1_900_091_005, "请填写不超过1000字的延期原因");
    ErrorCode DEFER_CONFLICT = new ErrorCode(1_900_091_006, "交付阶段已变更，请刷新后重试");
}
