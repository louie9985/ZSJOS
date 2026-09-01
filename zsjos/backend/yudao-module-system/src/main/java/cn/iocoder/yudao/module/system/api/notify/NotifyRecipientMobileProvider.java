package cn.iocoder.yudao.module.system.api.notify;

/** Resolves a mobile number for a notification identity owned by another module. */
public interface NotifyRecipientMobileProvider {

    Integer getUserType();

    String getMobile(Long userId);
}
