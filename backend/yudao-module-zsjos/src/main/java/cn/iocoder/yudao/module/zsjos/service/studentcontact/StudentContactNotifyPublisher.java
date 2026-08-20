package cn.iocoder.yudao.module.zsjos.service.studentcontact;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.system.api.notify.NotifyBusinessEventApi;
import cn.iocoder.yudao.module.system.api.notify.dto.NotifyBusinessEvent;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

import static cn.iocoder.yudao.module.zsjos.service.studentcontact.StudentContactConstants.BIZ_TYPE;

@Component
public class StudentContactNotifyPublisher {

    @Resource
    private NotifyBusinessEventApi notifyBusinessEventApi;

    public void publish(String sceneCode, Long relationId, String sourceEventKey, Long targetRuleId,
                        LocalDateTime occurredAt, Map<String, Object> context) {
        notifyBusinessEventApi.publish(NotifyBusinessEvent.builder()
                .tenantId(TenantContextHolder.getRequiredTenantId())
                .sceneCode(sceneCode).sourceEventKey(sourceEventKey).targetRuleId(targetRuleId)
                .bizType(BIZ_TYPE).bizId(relationId).occurredAt(occurredAt)
                .payload(new LinkedHashMap<>(context == null ? Map.of() : context)).build());
    }
}
