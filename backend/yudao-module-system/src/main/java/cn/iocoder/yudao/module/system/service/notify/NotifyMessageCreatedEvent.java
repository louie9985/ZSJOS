package cn.iocoder.yudao.module.system.service.notify;

/**
 * 站内信持久化完成事件。
 *
 * @param messageId 站内信编号
 * @param userId 接收用户编号
 * @param userType 接收用户类型
 */
public record NotifyMessageCreatedEvent(Long messageId, Long userId, Integer userType) {
}
