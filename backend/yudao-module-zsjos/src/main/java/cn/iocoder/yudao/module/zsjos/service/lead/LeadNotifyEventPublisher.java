package cn.iocoder.yudao.module.zsjos.service.lead;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.system.api.notify.NotifyBusinessEventApi;
import cn.iocoder.yudao.module.system.api.notify.dto.NotifyBusinessEvent;
import cn.iocoder.yudao.module.system.api.notify.dto.NotifySendResult;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

import static cn.iocoder.yudao.module.zsjos.enums.LeadConstants.BIZ_TYPE_LEAD;

@Component
public class LeadNotifyEventPublisher {

    @Resource private NotifyBusinessEventApi notifyBusinessEventApi;

    public void publish(String sceneCode, Long leadId, String sourceEventKey, Long operatorUserId,
                        LocalDateTime occurredAt, Map<String, Object> context) {
        publish(sceneCode, leadId, sourceEventKey, null, operatorUserId, occurredAt, context);
    }

    public void publish(String sceneCode, Long leadId, String sourceEventKey, Long targetRuleId,
                        Long operatorUserId, LocalDateTime occurredAt, Map<String, Object> context) {
        Map<String, Object> payload = new LinkedHashMap<>(context == null ? Map.of() : context);
        payload.put("operatorUserId", operatorUserId);
        notifyBusinessEventApi.publish(NotifyBusinessEvent.builder()
                .tenantId(TenantContextHolder.getRequiredTenantId()).sceneCode(sceneCode)
                .sourceEventKey(sourceEventKey).targetRuleId(targetRuleId).bizType(BIZ_TYPE_LEAD).bizId(leadId)
                .operatorUserId(operatorUserId).occurredAt(occurredAt).payload(payload).build());
    }

    public NotifySendResult publishConfirmed(String sceneCode, Long leadId, String sourceEventKey, Long targetRuleId,
                                             Long operatorUserId, LocalDateTime occurredAt,
                                             Map<String, Object> context) {
        Map<String, Object> payload = new LinkedHashMap<>(context == null ? Map.of() : context);
        payload.put("operatorUserId", operatorUserId);
        return notifyBusinessEventApi.publishConfirmed(NotifyBusinessEvent.builder()
                .tenantId(TenantContextHolder.getRequiredTenantId()).sceneCode(sceneCode)
                .sourceEventKey(sourceEventKey).targetRuleId(targetRuleId).bizType(BIZ_TYPE_LEAD).bizId(leadId)
                .operatorUserId(operatorUserId).occurredAt(occurredAt).payload(payload).build());
    }
}
