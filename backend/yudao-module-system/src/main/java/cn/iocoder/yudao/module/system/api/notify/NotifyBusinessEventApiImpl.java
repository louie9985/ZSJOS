package cn.iocoder.yudao.module.system.api.notify;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.system.api.notify.dto.NotifyBusinessEvent;
import jakarta.annotation.Resource;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Objects;

@Service
public class NotifyBusinessEventApiImpl implements NotifyBusinessEventApi {

    @Resource
    private ApplicationEventPublisher eventPublisher;

    @Override
    public void publish(NotifyBusinessEvent event) {
        Long tenantId = event.getTenantId() != null
                ? event.getTenantId() : TenantContextHolder.getRequiredTenantId();
        LocalDateTime occurredAt = Objects.requireNonNullElseGet(event.getOccurredAt(), LocalDateTime::now);
        eventPublisher.publishEvent(NotifyBusinessEvent.builder()
                .tenantId(tenantId).sceneCode(event.getSceneCode()).sourceEventKey(event.getSourceEventKey())
                .targetRuleId(event.getTargetRuleId())
                .bizType(event.getBizType()).bizId(event.getBizId()).operatorUserId(event.getOperatorUserId())
                .occurredAt(occurredAt).payload(event.getPayload()).build());
    }
}
