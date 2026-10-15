package cn.iocoder.yudao.module.system.enums;

import cn.iocoder.yudao.framework.common.exception.ErrorCode;

/** Configuration and binding failures that callers can distinguish from infrastructure outages. */
public interface SocialAuthErrorCodes {
    ErrorCode SOURCE_UNSUPPORTED = new ErrorCode(1_002_018_002, "不支持的社交授权来源");
    ErrorCode SOURCE_CONFIGURATION_INVALID = new ErrorCode(1_002_018_003, "社交授权应用未配置或配置不完整，请联系管理员");
    ErrorCode WECOM_BINDING_CONFLICT = new ErrorCode(1_002_018_004, "企业微信身份已被占用，请联系管理员核实已有绑定");
}
