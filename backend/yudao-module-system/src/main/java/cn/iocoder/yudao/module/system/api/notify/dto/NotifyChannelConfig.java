package cn.iocoder.yudao.module.system.api.notify.dto;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class NotifyChannelConfig {
    Long tenantId;
    String channelCode;
    String provider;
    String configJson;
    Boolean enabled;
}
