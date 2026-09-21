package cn.iocoder.yudao.module.system.service.notify;

import cn.iocoder.yudao.module.system.api.notify.dto.NotifyDeliveryContext;
import lombok.Data;
import java.util.List;
import java.util.Map;

/** Versioned internal outbox envelope; never expose rendered content or business payload in query APIs. */
@Data
public class WecomOutboxPayload {
    public static final String FORMAT = "wecom-outbox-v1";
    private String deliveryFormat = FORMAT;
    private Map<String, Object> eventPayload;
    private List<Recipient> recipients;

    @Data
    public static class Recipient {
        private NotifyDeliveryContext context;
        private String status = "pending";
        private int attempts;
        private boolean retryable;
        private String errorCode;
        private String providerMessageId;
    }
}
