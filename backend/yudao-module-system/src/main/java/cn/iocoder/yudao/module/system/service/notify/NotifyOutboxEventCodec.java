package cn.iocoder.yudao.module.system.service.notify;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.system.api.notify.dto.NotifyBusinessEvent;
import cn.iocoder.yudao.module.system.api.notify.dto.NotifyRecipientDTO;
import cn.iocoder.yudao.module.system.dal.dataobject.notify.NotifyBusinessOutboxDO;
import lombok.Data;

import java.util.List;
import java.util.Map;
import java.util.HashSet;
import java.util.Set;
import java.util.Objects;
import java.time.LocalDateTime;

/** Owns persisted event metadata separately from scene variables and WeCom recipient checkpoints. */
final class NotifyOutboxEventCodec {
    static final String FIXED_FORMAT = "notify-fixed-event-v1";

    private NotifyOutboxEventCodec() {}

    static String encode(NotifyBusinessEvent event, String channel) {
        var fixed = event.validatedFixedRecipients();
        if ("wecom".equals(channel)) {
            var envelope = new WecomOutboxPayload();
            envelope.setEventPayload(event.getPayload());
            envelope.setRecipientMode(event.getRecipientMode());
            envelope.setFixedRecipients("FIXED".equals(event.getRecipientMode()) ? fixed : null);
            return JsonUtils.toJsonString(envelope);
        }
        if ("RULE".equals(event.getRecipientMode())) return JsonUtils.toJsonString(event.getPayload());
        var envelope = new FixedEnvelope();
        envelope.setEventPayload(event.getPayload());
        envelope.setFixedRecipients(fixed);
        return JsonUtils.toJsonString(envelope);
    }

    @SuppressWarnings("unchecked")
    static Decoded decode(NotifyBusinessOutboxDO row) {
        Map<String, Object> raw = row.getPayload() == null ? Map.of()
                : JsonUtils.parseObject(row.getPayload(), Map.class);
        boolean wecom = raw != null && WecomOutboxPayload.FORMAT.equals(raw.get("deliveryFormat"));
        boolean fixedEnvelope = raw != null && FIXED_FORMAT.equals(raw.get("deliveryFormat"));
        if (raw != null && raw.get("deliveryFormat") instanceof String format
                && (format.startsWith("notify-fixed-event-") || format.startsWith("wecom-outbox-"))
                && !wecom && !fixedEnvelope) {
            throw new IllegalArgumentException("Unsupported notification outbox format");
        }
        Map<String, Object> variables = raw;
        String mode = "RULE";
        List<NotifyRecipientDTO> recipients = null;
        if (wecom) {
            var envelope = JsonUtils.parseObject(row.getPayload(), WecomOutboxPayload.class);
            variables = envelope.getEventPayload();
            // Old envelopes have no mode. New fixed metadata must survive every checkpoint rewrite.
            mode = envelope.getRecipientMode() == null ? "RULE" : envelope.getRecipientMode();
            recipients = envelope.getFixedRecipients();
        } else if (fixedEnvelope) {
            var envelope = JsonUtils.parseObject(row.getPayload(), FixedEnvelope.class);
            variables = envelope.getEventPayload();
            mode = "FIXED";
            recipients = envelope.getFixedRecipients();
        }
        var event = NotifyBusinessEvent.builder().tenantId(row.getTenantId()).sceneCode(row.getSceneCode())
                .sourceEventKey(row.getSourceEventKey()).targetRuleId(row.getTargetRuleId())
                .bizType(row.getBizType()).bizId(row.getBizId()).operatorUserId(row.getOperatorUserId())
                .occurredAt(row.getOccurredAt()).payload(variables).recipientMode(mode).fixedRecipients(recipients).build();
        event.validatedFixedRecipients();
        if (fixedEnvelope) validateCheckpoints(JsonUtils.parseObject(row.getPayload(), FixedEnvelope.class));
        if (wecom && "FIXED".equals(event.getRecipientMode()))
            validateWecomCheckpoints(JsonUtils.parseObject(row.getPayload(), WecomOutboxPayload.class), event);
        return new Decoded(event, wecom);
    }

    static void validateWecomCheckpoints(WecomOutboxPayload envelope, NotifyBusinessEvent event) {
        if (!"FIXED".equals(event.getRecipientMode())) return;
        if (!"FIXED".equals(envelope.getRecipientMode())
                || !Objects.equals(event.validatedFixedRecipients(), envelope.getFixedRecipients()))
            throw new IllegalArgumentException("Fixed WeCom metadata mismatch");
        if (envelope.getRecipients() == null) return;
        Set<NotifyRecipientDTO> fixed = new HashSet<>(event.validatedFixedRecipients());
        Set<NotifyRecipientDTO> seen = new HashSet<>();
        for (var item : envelope.getRecipients()) {
            if (item == null || item.getContext() == null)
                throw new IllegalArgumentException("Missing fixed WeCom checkpoint");
            var context = item.getContext();
            var identity = new NotifyRecipientDTO(context.getUserType(), context.getUserId());
            if (!fixed.contains(identity) || !seen.add(identity)
                    || !Objects.equals(context.getTenantId(), event.getTenantId())
                    || !Objects.equals(context.getRuleId(), event.getTargetRuleId())
                    || !Objects.equals(context.getSceneCode(), event.getSceneCode())
                    || !Objects.equals(context.getSourceEventKey(), event.getSourceEventKey())
                    || item.getStatus() == null
                    || !Set.of("pending", "sending", "succeeded", "skipped", "failed", "uncertain").contains(item.getStatus()))
                throw new IllegalArgumentException("Invalid fixed WeCom checkpoint");
        }
        if (!seen.equals(fixed)) throw new IllegalArgumentException("Incomplete fixed WeCom checkpoints");
    }

    static void validateCheckpoints(FixedEnvelope envelope) {
        if (envelope.getRecipients() == null) return; // Previously queued fixed events have no checkpoints.
        Set<NotifyRecipientDTO> fixed = new HashSet<>(envelope.getFixedRecipients());
        Set<NotifyRecipientDTO> seen = new HashSet<>();
        for (var item : envelope.getRecipients()) {
            if (item == null || !fixed.contains(item.getIdentity()) || !seen.add(item.getIdentity())
                    || item.getStatus() == null || !Set.of("pending", "succeeded", "skipped", "failed").contains(item.getStatus())
                    || "succeeded".equals(item.getStatus()) && (item.getMessageId() == null
                        || item.getMessageId() <= 0 || item.getCompletedTime() == null)
                    || "skipped".equals(item.getStatus()) && (item.getErrorCode() == null
                        || item.getCompletedTime() == null))
                throw new IllegalArgumentException("Invalid fixed in-app checkpoint");
        }
        if (!seen.equals(fixed)) throw new IllegalArgumentException("Incomplete fixed in-app checkpoints");
    }

    record Decoded(NotifyBusinessEvent event, boolean wecom) {}

    @Data
    public static class FixedEnvelope {
        private String deliveryFormat = FIXED_FORMAT;
        private Map<String, Object> eventPayload;
        private List<NotifyRecipientDTO> fixedRecipients;
        private List<InAppRecipient> recipients;
    }

    @Data
    public static class InAppRecipient {
        private NotifyRecipientDTO identity;
        private String status = "pending";
        private String errorCode;
        private Long messageId;
        private LocalDateTime completedTime;
    }
}
