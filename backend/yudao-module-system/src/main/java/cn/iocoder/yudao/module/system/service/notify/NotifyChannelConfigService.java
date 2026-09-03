package cn.iocoder.yudao.module.system.service.notify;

import cn.iocoder.yudao.module.system.api.notify.dto.NotifyChannelConfig;

public interface NotifyChannelConfigService {
    NotifyChannelConfig getEnabled(Long tenantId, String channelCode);

    NotifyChannelConfig get(String channelCode);

    void updateEnabled(String channelCode, boolean enabled);
}
