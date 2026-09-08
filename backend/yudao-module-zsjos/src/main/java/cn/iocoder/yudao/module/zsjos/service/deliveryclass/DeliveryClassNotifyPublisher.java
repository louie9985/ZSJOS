package cn.iocoder.yudao.module.zsjos.service.deliveryclass;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.system.api.notify.NotifyBusinessEventApi;
import cn.iocoder.yudao.module.system.api.notify.dto.NotifyBusinessEvent;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class DeliveryClassNotifyPublisher {
    @Resource private NotifyBusinessEventApi notifyApi;

    public void publishOwnerChanged(String eventKey, Long relationId, Long classId, Long oldOwnerId,
                                    Long newOwnerId, String reason) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("serviceRelationId", relationId);
        payload.put("classId", classId);
        payload.put("previousOwnerUserId", oldOwnerId);
        payload.put("newOwnerUserId", newOwnerId);
        payload.put("reason", reason);
        notifyApi.publish(NotifyBusinessEvent.builder().tenantId(TenantContextHolder.getRequiredTenantId())
                .sceneCode(DeliveryClassNotifySceneProvider.SCENE_OWNER_CHANGED).sourceEventKey(eventKey)
                .bizType("service_relation").bizId(relationId).occurredAt(LocalDateTime.now())
                .payload(payload).build());
    }
}
