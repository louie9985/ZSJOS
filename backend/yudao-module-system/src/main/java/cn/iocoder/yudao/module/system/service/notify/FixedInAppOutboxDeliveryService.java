package cn.iocoder.yudao.module.system.service.notify;

import cn.iocoder.yudao.framework.common.enums.CommonStatusEnum;
import cn.iocoder.yudao.framework.common.enums.UserTypeEnum;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.tenant.core.util.TenantUtils;
import cn.iocoder.yudao.module.system.api.notify.dto.NotifyBusinessEvent;
import cn.iocoder.yudao.module.system.api.notify.dto.NotifyRecipientDTO;
import cn.iocoder.yudao.module.system.api.notify.dto.NotifySendResult;
import cn.iocoder.yudao.module.system.dal.dataobject.notify.NotifyBusinessOutboxDO;
import cn.iocoder.yudao.module.system.dal.dataobject.notify.NotifyMessageDO;
import cn.iocoder.yudao.module.system.dal.mysql.notify.NotifyBusinessOutboxMapper;
import cn.iocoder.yudao.module.system.dal.mysql.notify.NotifyMessageMapper;
import cn.iocoder.yudao.module.system.dal.mysql.user.AdminUserMapper;
import jakarta.annotation.Resource;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

/** Durable per-person outcomes reuse the existing outbox lease and idempotent message transaction. */
@Service
public class FixedInAppOutboxDeliveryService {
    @Resource private NotifyBusinessEventProcessor processor;
    @Resource private NotifyBusinessMessageCreator creator;
    @Resource private NotifyBusinessOutboxMapper outboxes;
    @Resource private NotifyMessageMapper messages;
    @Resource private AdminUserMapper users;

    public NotifySendResult deliver(NotifyBusinessOutboxDO row, NotifyBusinessEvent event) {
        // Never accept a caller-supplied roster instead of the claimed persisted envelope.
        var decoded = NotifyOutboxEventCodec.decode(row);
        if (decoded.wecom() || !"FIXED".equals(decoded.event().getRecipientMode())
                || !decoded.event().equals(event)) throw new IllegalArgumentException("Fixed event mismatch");
        AtomicReference<NotifySendResult> result = new AtomicReference<>();
        TenantUtils.execute(row.getTenantId(), () -> result.set(deliverInTenant(row, event)));
        return result.get();
    }

    private NotifySendResult deliverInTenant(NotifyBusinessOutboxDO row, NotifyBusinessEvent event) {
        var state = JsonUtils.parseObject(row.getPayload(), NotifyOutboxEventCodec.FixedEnvelope.class);
        var prepared = processor.prepareFixedInApp(event);
        if (prepared.failure() != null) return prepared.failure();
        if (state.getRecipients() == null) {
            state.setRecipients(new ArrayList<>());
            for (var identity : event.validatedFixedRecipients()) {
                var item = new NotifyOutboxEventCodec.InAppRecipient();
                item.setIdentity(identity);
                state.getRecipients().add(item);
            }
            checkpoint(row, state);
        }
        boolean failed = false;
        for (var item : state.getRecipients()) {
            if ("succeeded".equals(item.getStatus()) || "skipped".equals(item.getStatus())) continue;
            // A worker can die after REQUIRES_NEW commits but before its checkpoint. Preserve that success,
            // even if the employee has since been disabled; do not create a second message or mislabel a skip.
            var persisted = findMessage(row, item.getIdentity());
            if (persisted != null) {
                succeeded(item, persisted);
                checkpoint(row, state);
                continue;
            }
            String skip = employeeSkipReason(row, item.getIdentity());
            if (skip == null) skip = processor.deliverySkipReason(event);
            if (skip != null) {
                item.setStatus("skipped"); item.setErrorCode(skip); item.setCompletedTime(LocalDateTime.now());
                checkpoint(row, state);
                continue;
            }
            try {
                creator.create(event, prepared.provider(), prepared.rule(), prepared.template(), item.getIdentity());
            } catch (DuplicateKeyException duplicate) {
                // Confirm the expected identity below; an unrelated unique-key failure is not delivery proof.
            } catch (RuntimeException failure) {
                item.setStatus("failed"); item.setErrorCode("NOTIFY_DELIVERY_FAILED");
                item.setCompletedTime(null);
                checkpoint(row, state);
                failed = true;
                continue;
            }
            persisted = findMessage(row, item.getIdentity());
            if (persisted == null) {
                item.setStatus("failed"); item.setErrorCode("NOTIFY_RECIPIENT_EVIDENCE_MISSING");
                item.setCompletedTime(null); failed = true;
            } else succeeded(item, persisted);
            checkpoint(row, state);
        }
        return failed ? NotifySendResult.failure("NOTIFY_RECIPIENT_FAILURE", "部分站内信接收人投递失败", true)
                : NotifySendResult.success(null);
    }

    private String employeeSkipReason(NotifyBusinessOutboxDO row, NotifyRecipientDTO identity) {
        if (!UserTypeEnum.ADMIN.getValue().equals(identity.getUserType())) return null;
        var user = users.selectById(identity.getUserId());
        return user == null || Boolean.TRUE.equals(user.getDeleted()) || !Objects.equals(row.getTenantId(), user.getTenantId())
                || !CommonStatusEnum.ENABLE.getStatus().equals(user.getStatus()) ? "EMPLOYEE_UNAVAILABLE" : null;
    }

    private NotifyMessageDO findMessage(NotifyBusinessOutboxDO row, NotifyRecipientDTO identity) {
        return messages.selectDeliveryEvidence(row.getTenantId(), row.getSceneCode(), row.getSourceEventKey(),
                row.getTargetRuleId(), List.of(identity)).stream().findFirst().orElse(null);
    }

    private void succeeded(NotifyOutboxEventCodec.InAppRecipient item, NotifyMessageDO message) {
        item.setStatus("succeeded"); item.setErrorCode(null); item.setMessageId(message.getId());
        item.setCompletedTime(message.getCreateTime());
    }

    private void checkpoint(NotifyBusinessOutboxDO row, NotifyOutboxEventCodec.FixedEnvelope state) {
        NotifyOutboxEventCodec.validateCheckpoints(state);
        var now = LocalDateTime.now();
        String payload = JsonUtils.toJsonString(state);
        if (outboxes.checkpoint(row.getId(), row.getTenantId(), row.getClaimToken(), payload, now, now.plusMinutes(2)) != 1)
            throw new CannotAcquireLockException("Fixed in-app outbox delivery lease lost");
        row.setPayload(payload);
    }
}
