package cn.iocoder.yudao.module.system.api.notify;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.framework.tenant.core.util.TenantUtils;
import cn.iocoder.yudao.module.system.api.notify.dto.NotifyBusinessEvent;
import cn.iocoder.yudao.module.system.api.notify.dto.NotifySendResult;
import cn.iocoder.yudao.module.system.service.notify.NotifyBusinessEventProcessor;
import cn.iocoder.yudao.module.system.service.notify.NotifyBusinessOutboxService;
import cn.iocoder.yudao.module.system.service.notify.NotifyRuleService;
import jakarta.annotation.Resource;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Objects;

@Service
public class NotifyBusinessEventApiImpl implements NotifyBusinessEventApi {

    @Resource
    private NotifyBusinessOutboxService outboxService;
    @Resource
    private NotifyRuleService notifyRuleService;
    @Resource
    private NotifyBusinessEventProcessor eventProcessor;
    @Resource
    private ApplicationEventPublisher applicationEventPublisher;

    @Override
    @Transactional(propagation = Propagation.REQUIRED)
    public void publish(NotifyBusinessEvent event) {
        NotifyBusinessEvent normalized = normalize(event);
        TenantUtils.execute(normalized.getTenantId(), () -> {
            var rules = notifyRuleService.getEnabledRules(normalized.getSceneCode()).stream()
                    .filter(rule -> normalized.getTargetRuleId() == null
                            || normalized.getTargetRuleId().equals(rule.getId()))
                    .toList();
            var inAppRules = rules.stream()
                    .filter(rule -> rule.getChannelCode() == null || rule.getChannelCode().isBlank()
                            || NotifyChannelType.IN_APP.equals(rule.getChannelCode()))
                    .toList();
            if (!inAppRules.isEmpty()) {
                outboxService.enqueue(normalized, inAppRules);
            }
            rules.stream()
                    .filter(rule -> rule.getChannelCode() != null && !rule.getChannelCode().isBlank())
                    .filter(rule -> !NotifyChannelType.IN_APP.equals(rule.getChannelCode()))
                    .filter(rule -> !NotifyChannelType.WEBSOCKET.equals(rule.getChannelCode()))
                    .forEach(rule -> applicationEventPublisher.publishEvent(copyForRule(normalized, rule.getId())));
        });
    }

    @Override
    public NotifySendResult publishConfirmed(NotifyBusinessEvent event) {
        return eventProcessor.processConfirmed(normalize(event));
    }

    private NotifyBusinessEvent normalize(NotifyBusinessEvent event) {
        Long tenantId = event.getTenantId() != null
                ? event.getTenantId() : TenantContextHolder.getRequiredTenantId();
        LocalDateTime occurredAt = Objects.requireNonNullElseGet(event.getOccurredAt(), LocalDateTime::now);
        return NotifyBusinessEvent.builder()
                .tenantId(tenantId).sceneCode(event.getSceneCode()).sourceEventKey(event.getSourceEventKey())
                .targetRuleId(event.getTargetRuleId())
                .bizType(event.getBizType()).bizId(event.getBizId()).operatorUserId(event.getOperatorUserId())
                .occurredAt(occurredAt).payload(event.getPayload()).build();
    }

    private NotifyBusinessEvent copyForRule(NotifyBusinessEvent event, Long targetRuleId) {
        return NotifyBusinessEvent.builder()
                .tenantId(event.getTenantId()).sceneCode(event.getSceneCode())
                .sourceEventKey(event.getSourceEventKey()).targetRuleId(targetRuleId)
                .bizType(event.getBizType()).bizId(event.getBizId()).operatorUserId(event.getOperatorUserId())
                .occurredAt(event.getOccurredAt()).payload(event.getPayload()).build();
    }
}
