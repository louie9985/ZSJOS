package cn.iocoder.yudao.module.zsjos.enums;

import cn.iocoder.yudao.framework.common.exception.ErrorCode;

public interface CalendarRestDayErrorCodes {
    ErrorCode DENIED = new ErrorCode(1_900_018_060, "无权访问首页休息日");
    ErrorCode CONFLICT = new ErrorCode(1_900_018_061, "休息日已被其他人更新，请刷新后核对重试");
    ErrorCode INVALID = new ErrorCode(1_900_018_062, "日期范围或批量变更无效");
}
