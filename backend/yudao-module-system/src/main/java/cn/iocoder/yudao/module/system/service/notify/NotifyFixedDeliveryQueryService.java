package cn.iocoder.yudao.module.system.service.notify;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.system.api.notify.dto.NotifyDeliveryPageDTO;
import cn.iocoder.yudao.module.system.api.notify.dto.NotifyRecipientDTO;
import cn.iocoder.yudao.module.system.dal.dataobject.notify.NotifyBusinessOutboxDO;
import cn.iocoder.yudao.module.system.dal.mysql.notify.NotifyBusinessOutboxMapper;
import cn.iocoder.yudao.module.system.dal.mysql.notify.NotifyMessageMapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import java.util.*;
import static cn.iocoder.yudao.module.system.api.notify.dto.NotifyDeliveryPageDTO.Status.*;

/** Projects System-owned facts without treating a rule's aggregate status as per-user success. */
@Service
public class NotifyFixedDeliveryQueryService {
    @Resource private NotifyBusinessOutboxMapper outboxes;
    @Resource private NotifyMessageMapper messages;

    public NotifyDeliveryPageDTO query(String scene, String eventKey, List<NotifyRecipientDTO> requested,
                                       long afterId, int limit) {
        Long tenantId = TenantContextHolder.getRequiredTenantId();
        if (scene == null || scene.isBlank() || scene.length() > 64 || eventKey == null || eventKey.isBlank()
                || eventKey.length() > 128 || afterId < 0 || limit < 1 || limit > 100
                || requested == null || requested.isEmpty() || requested.size() > 200)
            throw new IllegalArgumentException("Invalid bounded notification evidence query");
        // Reuse the typed identity contract, including calendar-only ADMIN constraints.
        var identities = cn.iocoder.yudao.module.system.api.notify.dto.NotifyBusinessEvent.builder()
                .sceneCode(scene).recipientMode("FIXED").fixedRecipients(requested).build().validatedFixedRecipients();
        var rows = outboxes.selectEventEvidence(tenantId, scene, eventKey, afterId, limit + 1);
        boolean more = rows.size() > limit;
        var page = rows.stream().limit(limit).map(row -> {
            if (!Objects.equals(row.getTenantId(), tenantId) || !Objects.equals(row.getSceneCode(), scene)
                    || !Objects.equals(row.getSourceEventKey(), eventKey) || Boolean.TRUE.equals(row.getDeleted()))
                throw new IllegalStateException("Notification evidence scope mismatch");
            return project(row, identities);
        }).toList();
        return new NotifyDeliveryPageDTO(page, page.isEmpty() ? afterId : page.getLast().outboxId(), more);
    }

    private NotifyDeliveryPageDTO.Rule project(NotifyBusinessOutboxDO row, List<NotifyRecipientDTO> requested) {
        NotifyOutboxEventCodec.Decoded decoded;
        try {
            decoded = NotifyOutboxEventCodec.decode(row);
            if (!"FIXED".equals(decoded.event().getRecipientMode()))
                return rule(row, "unknown", "NOTIFY_FIXED_EVIDENCE_UNAVAILABLE", List.of());
        } catch (RuntimeException invalid) {
            return rule(row, "unknown", "NOTIFY_PAYLOAD_INVALID", List.of());
        }
        Set<NotifyRecipientDTO> fixed = new HashSet<>(decoded.event().validatedFixedRecipients());
        var selected = requested.stream().filter(fixed::contains).toList();
        if (selected.isEmpty()) return rule(row, decoded.wecom() ? "wecom" : "in_app", safeCode(row.getLastError()), List.of());
        if (!decoded.wecom()) return inApp(row, selected);
        var payload = JsonUtils.parseObject(row.getPayload(), WecomOutboxPayload.class);
        Map<NotifyRecipientDTO, WecomOutboxPayload.Recipient> checkpoints = new HashMap<>();
        if (payload.getRecipients() != null) for (var item : payload.getRecipients()) {
            if (item == null || item.getContext() == null) return rule(row, "wecom", "NOTIFY_PAYLOAD_INVALID", List.of());
            var context = item.getContext();
            var identity = new NotifyRecipientDTO(context.getUserType(), context.getUserId());
            if (!fixed.contains(identity) || !Objects.equals(context.getTenantId(), row.getTenantId())
                    || !Objects.equals(context.getRuleId(), row.getTargetRuleId())
                    || !Objects.equals(context.getSceneCode(), row.getSceneCode())
                    || !Objects.equals(context.getSourceEventKey(), row.getSourceEventKey())
                    || checkpoints.put(identity, item) != null)
                return rule(row, "wecom", "NOTIFY_PAYLOAD_INVALID", List.of());
        }
        return rule(row, "wecom", safeCode(row.getLastError()), selected.stream().map(identity -> {
            var checkpoint = checkpoints.get(identity);
            if (checkpoint == null) return unresolved(row, identity);
            var status = switch (Objects.toString(checkpoint.getStatus(), "")) {
                case "succeeded" -> SUCCEEDED;
                case "skipped" -> SKIPPED;
                case "failed" -> FAILED;
                case "uncertain" -> UNCERTAIN;
                // An in-flight external request must never become eligible for blind retry.
                case "sending" -> UNCERTAIN;
                case "pending" -> waiting(row) ? PENDING : unresolved(row, identity).status();
                default -> UNKNOWN;
            };
            return new NotifyDeliveryPageDTO.Recipient(identity.getUserType(), identity.getUserId(), status,
                    "sending".equals(checkpoint.getStatus()) ? "WECOM_DELIVERY_UNCONFIRMED" : safeCode(checkpoint.getErrorCode()),
                    null, "WECOM_RECIPIENT_SKIPPED".equals(checkpoint.getProviderMessageId()) ? null : checkpoint.getProviderMessageId(),
                    status == FAILED && checkpoint.isRetryable() && waiting(row),
                    status == SUCCEEDED || status == SKIPPED || status == FAILED && !checkpoint.isRetryable()
                            ? checkpoint.getCompletedTime() : null);
        }).toList());
    }

    private NotifyDeliveryPageDTO.Rule inApp(NotifyBusinessOutboxDO row, List<NotifyRecipientDTO> selected) {
        var envelope = JsonUtils.parseObject(row.getPayload(), NotifyOutboxEventCodec.FixedEnvelope.class);
        Map<NotifyRecipientDTO, NotifyOutboxEventCodec.InAppRecipient> checkpoints = new HashMap<>();
        if (envelope.getRecipients() != null) envelope.getRecipients().forEach(item -> checkpoints.put(item.getIdentity(), item));
        var persisted = messages.selectDeliveryEvidence(row.getTenantId(), row.getSceneCode(), row.getSourceEventKey(),
                row.getTargetRuleId(), selected);
        Map<NotifyRecipientDTO, cn.iocoder.yudao.module.system.dal.dataobject.notify.NotifyMessageDO> evidence = new HashMap<>();
        for (var message : persisted) evidence.put(new NotifyRecipientDTO(message.getUserType(), message.getUserId()), message);
        return rule(row, "in_app", safeCode(row.getLastError()), selected.stream().map(identity -> {
            var message = evidence.get(identity);
            if (message == null) {
                var checkpoint = checkpoints.get(identity);
                if (checkpoint == null || "pending".equals(checkpoint.getStatus())) return unresolved(row, identity);
                var status = switch (checkpoint.getStatus()) {
                    case "succeeded" -> SUCCEEDED;
                    case "skipped" -> SKIPPED;
                    case "failed" -> FAILED;
                    default -> UNKNOWN;
                };
                return new NotifyDeliveryPageDTO.Recipient(identity.getUserType(), identity.getUserId(), status,
                        safeCode(checkpoint.getErrorCode()), status == SUCCEEDED ? checkpoint.getMessageId() : null,
                        null, status == FAILED && waiting(row), status == SUCCEEDED || status == SKIPPED
                            ? checkpoint.getCompletedTime() : null);
            }
            return new NotifyDeliveryPageDTO.Recipient(identity.getUserType(), identity.getUserId(), SUCCEEDED,
                    null, message.getId(), null, false, message.getCreateTime());
        }).toList());
    }

    private NotifyDeliveryPageDTO.Recipient unresolved(NotifyBusinessOutboxDO row, NotifyRecipientDTO identity) {
        var status = switch (Objects.toString(row.getStatus(), "")) {
            case "pending", "processing" -> PENDING;
            case "failed" -> FAILED;
            case "skipped" -> SKIPPED;
            default -> UNKNOWN;
        };
        return new NotifyDeliveryPageDTO.Recipient(identity.getUserType(), identity.getUserId(), status,
                status == UNKNOWN ? "NOTIFY_RECIPIENT_EVIDENCE_MISSING" : safeCode(row.getLastError()),
                null, null, false, null);
    }
    private boolean waiting(NotifyBusinessOutboxDO row) {
        return "pending".equals(row.getStatus()) || "processing".equals(row.getStatus());
    }
    private NotifyDeliveryPageDTO.Rule rule(NotifyBusinessOutboxDO row, String channel, String error,
                                          List<NotifyDeliveryPageDTO.Recipient> recipients) {
        return new NotifyDeliveryPageDTO.Rule(row.getId(), row.getTargetRuleId(), channel, row.getStatus(), error, recipients);
    }
    private String safeCode(String raw) {
        if (raw == null) return null;
        String code = raw.split(":", 2)[0];
        return code.matches("[A-Z][A-Z0-9_\\-]{0,80}") ? code : "NOTIFY_DELIVERY_FAILED";
    }
}
