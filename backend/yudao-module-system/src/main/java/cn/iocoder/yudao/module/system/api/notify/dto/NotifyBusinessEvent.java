package cn.iocoder.yudao.module.system.api.notify.dto;

import lombok.Builder;
import lombok.Value;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Cross-module notification event. Payload is interpreted only by the provider that owns the scene.
 */
@Value
@Builder(toBuilder = true)
public class NotifyBusinessEvent {

    Long tenantId;
    String sceneCode;
    String sourceEventKey;
    Long targetRuleId;
    String bizType;
    Long bizId;
    Long operatorUserId;
    LocalDateTime occurredAt;
    Map<String, Object> payload;
    @Builder.Default String recipientMode = "RULE";
    java.util.List<NotifyRecipientDTO> fixedRecipients;

    /** Validate before persistence and after recovery; malformed fixed events never fall back to rules. */
    public java.util.List<NotifyRecipientDTO> validatedFixedRecipients() {
        boolean calendar = "zsjos.calendar.exam".equals(sceneCode) || "zsjos.calendar.course".equals(sceneCode);
        if (calendar && !"FIXED".equals(recipientMode)) {
            throw new IllegalArgumentException("Calendar notifications require fixed employees");
        }
        if (!"RULE".equals(recipientMode) && !"FIXED".equals(recipientMode)) {
            throw new IllegalArgumentException("Unknown notification recipient mode");
        }
        if ("RULE".equals(recipientMode)) {
            if (fixedRecipients != null && !fixedRecipients.isEmpty()) {
                throw new IllegalArgumentException("Rule event cannot contain fixed recipients");
            }
            return java.util.List.of();
        }
        if (fixedRecipients == null || fixedRecipients.isEmpty()) {
            throw new IllegalArgumentException("Fixed notification recipients are required");
        }
        java.util.Set<NotifyRecipientDTO> unique = new java.util.LinkedHashSet<>();
        for (NotifyRecipientDTO recipient : fixedRecipients) {
            if (recipient == null || recipient.getUserId() == null || recipient.getUserId() <= 0
                    || recipient.getUserType() == null
                    || cn.iocoder.yudao.framework.common.enums.UserTypeEnum.valueOf(recipient.getUserType()) == null) {
                throw new IllegalArgumentException("Invalid fixed notification identity");
            }
            if (calendar
                    && !cn.iocoder.yudao.framework.common.enums.UserTypeEnum.ADMIN.getValue().equals(recipient.getUserType())) {
                throw new IllegalArgumentException("Calendar recipients must be employees");
            }
            unique.add(new NotifyRecipientDTO(recipient.getUserType(), recipient.getUserId()));
        }
        return java.util.List.copyOf(unique);
    }
}
