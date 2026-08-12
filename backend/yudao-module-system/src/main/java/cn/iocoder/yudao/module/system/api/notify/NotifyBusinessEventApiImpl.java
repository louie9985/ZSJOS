package cn.iocoder.yudao.module.system.api.notify;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.system.api.notify.dto.NotifyBusinessEvent;
import cn.iocoder.yudao.module.system.api.notify.dto.NotifySendResult;
import cn.iocoder.yudao.module.system.service.notify.NotifyBusinessEventProcessor;
import jakarta.annotation.Resource;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Objects;

@Service
public class NotifyBusinessEventApiImpl implements NotifyBusinessEventApi {

    @Resource
    private ApplicationEventPublisher eventPublisher;
    @Resource
    private NotifyBusinessEventProcessor eventProcessor;

    @Override
    public void publish(NotifyBusinessEvent event) {
        eventPublisher.publishEvent(normalize(event));
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
}
