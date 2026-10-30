package cn.iocoder.yudao.module.zsjos.enums;
import cn.iocoder.yudao.framework.common.exception.ErrorCode;

public interface GiftPurchaseErrorCodes {
    ErrorCode DENIED = new ErrorCode(1_900_157_003, "无权操作该采购记录，请刷新或联系管理员");
    ErrorCode NOT_FOUND = new ErrorCode(1_900_157_000, "礼品采购记录不存在或已删除");
    ErrorCode EMPTY_GIFTS = new ErrorCode(1_900_157_001, "请至少选择一项礼品");
    ErrorCode ACTOR_INVALID = new ErrorCode(1_900_157_002, "操作人或录单销售不可用，请刷新后重试");
}
