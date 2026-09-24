package cn.iocoder.yudao.module.system.service.notify;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.tenant.core.util.TenantUtils;
import cn.iocoder.yudao.module.system.api.notify.dto.NotifyBusinessEvent;
import cn.iocoder.yudao.module.system.api.notify.dto.NotifySendResult;
import cn.iocoder.yudao.module.system.dal.dataobject.notify.NotifyBusinessOutboxDO;
import cn.iocoder.yudao.module.system.dal.mysql.notify.NotifyBusinessOutboxMapper;
import jakarta.annotation.Resource;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.stereotype.Service;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

@Service
public class WecomOutboxDeliveryService {
    @Resource private NotifyBusinessEventProcessor processor;
    @Resource private NotifyRuleService ruleService;
    @Resource private NotifyBusinessOutboxMapper outboxMapper;
    @Resource private WecomNotifyChannelAdapter adapter;

    public NotifySendResult deliver(NotifyBusinessOutboxDO row, NotifyBusinessEvent event) {
        AtomicReference<NotifySendResult> result = new AtomicReference<>();
        TenantUtils.execute(row.getTenantId(), () -> result.set(deliverInTenant(row, event)));
        return result.get();
    }

    private NotifySendResult deliverInTenant(NotifyBusinessOutboxDO row, NotifyBusinessEvent event) {
        if (ruleService.getEnabledRules(event.getSceneCode()).stream().noneMatch(rule ->
                Objects.equals(rule.getId(), row.getTargetRuleId()) && "wecom".equals(rule.getChannelCode()))) {
            return NotifySendResult.failure("NOTIFY_RULE_MISSING", "企微规则不存在、已停用或渠道已变更", false);
        }
        WecomOutboxPayload state = JsonUtils.parseObjectQuietly(row.getPayload(), WecomOutboxPayload.class);
        if (state == null) return NotifySendResult.failure("NOTIFY_PAYLOAD_INVALID", "企微投递快照无法解析", false);
        if (state.getRecipients() != null && state.getRecipients().stream()
                .anyMatch(recipient -> "pending".equals(recipient.getStatus()) || recipient.isRetryable())) {
            var rule = ruleService.getEnabledRules(event.getSceneCode()).stream()
                    .filter(value -> Objects.equals(value.getId(), row.getTargetRuleId())).findFirst().orElseThrow();
            NotifySendResult applicability = processor.evaluateRule(event, rule);
            if (applicability != null) return applicability;
        }
        if (state.getRecipients() == null) {
            var prepared = processor.prepareWecom(event);
            if (prepared.failure() != null) return prepared.failure();
            state.setRecipients(new ArrayList<>());
            for (var context : prepared.recipients()) {
                var recipient = new WecomOutboxPayload.Recipient();
                recipient.setContext(context);
                state.getRecipients().add(recipient);
            }
            checkpoint(row, state);
        }
        for (var recipient : state.getRecipients()) {
            if ("sending".equals(recipient.getStatus())) {
                // A worker died after recording intent. The provider may have accepted the message;
                // replaying it cannot be proven safe, so preserve uncertainty for operator inspection.
                recipient.setStatus("uncertain"); recipient.setRetryable(false);
                recipient.setErrorCode("WECOM_DELIVERY_UNCERTAIN");
                checkpoint(row, state);
                continue;
            }
            if (!"pending".equals(recipient.getStatus()) && !recipient.isRetryable()) continue;
            try {
                String skipReason = processor.deliverySkipReason(event);
                if (skipReason != null) {
                    recipient.setStatus("skipped"); recipient.setRetryable(false);
                    recipient.setErrorCode(skipReason);
                    checkpoint(row, state);
                    continue;
                }
                recipient.setContext(adapter.prepare(recipient.getContext()));
            } catch (RuntimeException exception) {
                recipient.setStatus("failed"); recipient.setRetryable(true);
                recipient.setErrorCode("WECOM_PREPARE_FAILED");
                checkpoint(row, state);
                continue;
            }
            recipient.setStatus("sending"); recipient.setAttempts(recipient.getAttempts() + 1);
            checkpoint(row, state);
            NotifySendResult result;
            try { result = adapter.send(recipient.getContext()); }
            catch (RuntimeException exception) {
                result = NotifySendResult.failure("WECOM_DELIVERY_UNCERTAIN", "无法确认企微是否已接收", false);
            }
            recipient.setRetryable(result.isRetryable());
            recipient.setErrorCode(result.getErrorCode());
            recipient.setProviderMessageId(result.getExternalId());
            recipient.setStatus(result.isSuccess()
                    ? "WECOM_RECIPIENT_SKIPPED".equals(result.getExternalId()) ? "skipped" : "succeeded"
                    : "WECOM_DELIVERY_UNCERTAIN".equals(result.getErrorCode()) ? "uncertain" : "failed");
            checkpoint(row, state);
        }
        boolean retryable = state.getRecipients().stream().anyMatch(WecomOutboxPayload.Recipient::isRetryable);
        boolean failed = state.getRecipients().stream().anyMatch(r -> "failed".equals(r.getStatus()) || "uncertain".equals(r.getStatus()));
        return failed ? NotifySendResult.failure("WECOM_RECIPIENT_FAILURE", "部分企微接收人投递失败或结果不确定", retryable)
                : NotifySendResult.success(null);
    }

    private void checkpoint(NotifyBusinessOutboxDO row, WecomOutboxPayload payload) {
        LocalDateTime now = LocalDateTime.now();
        String json = JsonUtils.toJsonString(payload);
        if (outboxMapper.checkpoint(row.getId(), row.getTenantId(), row.getClaimToken(), json, now, now.plusMinutes(2)) != 1) {
            throw new CannotAcquireLockException("WeCom outbox delivery lease lost");
        }
        row.setPayload(json);
    }
}
