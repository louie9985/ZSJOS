package cn.iocoder.yudao.module.system.api.notify.dto;

import java.time.LocalDateTime;
import java.util.List;

/** Content-free evidence, not a delivery command or rendered message projection. */
public record NotifyDeliveryPageDTO(List<Rule> rules, long nextCursor, boolean hasMore) {
    public enum Status { PENDING, SUCCEEDED, SKIPPED, FAILED, UNCERTAIN, UNKNOWN }
    public record Rule(Long outboxId, Long ruleId, String channelCode, String outboxStatus,
                       String errorCode, List<Recipient> recipients) { }
    public record Recipient(Integer userType, Long userId, Status status, String errorCode,
                            Long messageId, String providerMessageId, boolean retryScheduled,
                            LocalDateTime completedTime) { }
}
