package cn.iocoder.yudao.module.zsjos.enums;

import cn.iocoder.yudao.framework.common.exception.ErrorCode;

public interface ExamCreateErrorCodes {
    ErrorCode KEY_INVALID = new ErrorCode(1_900_018_015, "创建考期请求标识无效，请重新发起创建");
    ErrorCode KEY_CONFLICT = new ErrorCode(1_900_018_016, "创建考期请求标识已用于其他内容，请核对原请求");
}
