package cn.iocoder.yudao.module.zsjos.enums;

import cn.iocoder.yudao.framework.common.exception.ErrorCode;

public interface LeadSourceErrorCodes {
    ErrorCode LEAD_SOURCE_ATTRIBUTION_INVALID = new ErrorCode(1_900_093_001, "客资来源与提供方记录不一致，请重新确认录入信息");
    ErrorCode CONVERSION_SOURCE_REVIEW_REQUIRED = new ErrorCode(1_900_093_002, "来源待核实，暂无法计算成交率");
}
