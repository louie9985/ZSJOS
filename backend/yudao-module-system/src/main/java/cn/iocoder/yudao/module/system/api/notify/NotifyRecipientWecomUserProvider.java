package cn.iocoder.yudao.module.system.api.notify;

/** Resolves a WeCom userid for a typed notification recipient. */
public interface NotifyRecipientWecomUserProvider {

    Integer getUserType();

    String getWecomUserId(Long userId);

    /** Called only when no address was resolved; legacy providers retain a stable generic reason. */
    default String getUnavailableReason(Long userId) { return "WECOM_RECIPIENT_UNAVAILABLE"; }
}
