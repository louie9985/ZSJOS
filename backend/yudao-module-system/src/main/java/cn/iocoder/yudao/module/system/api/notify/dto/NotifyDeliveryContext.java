package cn.iocoder.yudao.module.system.api.notify.dto;

import lombok.Builder;
import lombok.Value;

import java.util.Map;

@Value
@Builder(toBuilder = true)
@tools.jackson.databind.annotation.JsonDeserialize(builder = NotifyDeliveryContext.NotifyDeliveryContextBuilder.class)
public class NotifyDeliveryContext {
    @tools.jackson.databind.annotation.JsonPOJOBuilder(withPrefix = "")
    public static class NotifyDeliveryContextBuilder {}

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
    /** Frozen before durable WeCom delivery so retries do not create different click tickets. */
    String wecomClickUrl;
    boolean wecomClickPrepared;
}
