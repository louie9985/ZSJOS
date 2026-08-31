package cn.iocoder.yudao.module.system.api.notify.dto;

import lombok.Builder;
import lombok.Value;

import java.util.Map;

@Value
@Builder
public class NotifyDeliveryContext {
    Long tenantId;
    String sceneCode;
    String sourceEventKey;
    Long ruleId;
    String actionType;
    Long userId;
    Integer userType;
    String templateCode;
    String smsTemplateId;
    String wecomMessageType;
    String title;
    String content;
    Map<String, Object> variables;
    String bizType;
    Long bizId;
}
