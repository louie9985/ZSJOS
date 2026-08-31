package cn.iocoder.yudao.module.system.api.notify;

/** Resolves a WeCom userid for a typed notification recipient. */
public interface NotifyRecipientWecomUserProvider {

    Integer getUserType();

    String getWecomUserId(Long userId);
}
