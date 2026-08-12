package cn.iocoder.yudao.module.system.api.notify.dto;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class NotifySendResult {
    boolean success;
    boolean retryable;
    String externalId;
    String errorCode;
    String errorMessage;

    public static NotifySendResult success(String externalId) {
        return NotifySendResult.builder().success(true).externalId(externalId).build();
    }

    public static NotifySendResult failure(String code, String message, boolean retryable) {
        return NotifySendResult.builder().success(false).errorCode(code)
                .errorMessage(message).retryable(retryable).build();
    }
}
