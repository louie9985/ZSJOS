package cn.iocoder.yudao.module.system.api.notify.dto;

import lombok.Builder;
import lombok.Value;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Cross-module notification event. Payload is interpreted only by the provider that owns the scene.
 */
@Value
@Builder
public class NotifyBusinessEvent {

    Long tenantId;
    String sceneCode;
    String sourceEventKey;
    Long targetRuleId;
    String bizType;
    Long bizId;
    Long operatorUserId;
    LocalDateTime occurredAt;
    Map<String, Object> payload;
}
