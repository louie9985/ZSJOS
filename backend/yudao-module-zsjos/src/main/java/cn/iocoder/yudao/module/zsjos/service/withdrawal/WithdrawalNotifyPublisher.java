package cn.iocoder.yudao.module.zsjos.service.withdrawal;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.system.api.notify.NotifyBusinessEventApi;
import cn.iocoder.yudao.module.system.api.notify.dto.NotifyBusinessEvent;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;
import java.time.LocalDateTime;
import java.util.Map;

@Component
public class WithdrawalNotifyPublisher {
    @Resource private NotifyBusinessEventApi api;
    public void publish(String scene, Long withdrawalId, String key, Long operator, Map<String, Object> payload) {
        api.publish(NotifyBusinessEvent.builder().tenantId(TenantContextHolder.getRequiredTenantId())
                .sceneCode(scene).sourceEventKey(key).bizType("withdrawal").bizId(withdrawalId)
                .operatorUserId(operator).occurredAt(LocalDateTime.now()).payload(payload).build());
    }
}
