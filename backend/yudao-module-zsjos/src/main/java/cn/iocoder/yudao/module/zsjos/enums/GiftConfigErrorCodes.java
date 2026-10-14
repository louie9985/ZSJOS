package cn.iocoder.yudao.module.zsjos.enums;

import cn.iocoder.yudao.framework.common.exception.ErrorCode;

public interface GiftConfigErrorCodes {
    ErrorCode CODE_EXISTS = new ErrorCode(1_900_095_000, "礼品编码已存在，请使用其他编码");
}
