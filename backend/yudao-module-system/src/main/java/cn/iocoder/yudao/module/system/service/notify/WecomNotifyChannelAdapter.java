package cn.iocoder.yudao.module.system.service.notify;

import cn.iocoder.yudao.module.system.api.notify.NotifyChannelAdapter;
import cn.iocoder.yudao.module.system.api.notify.NotifyChannelType;
import cn.iocoder.yudao.module.system.api.notify.dto.NotifyDeliveryContext;
import cn.iocoder.yudao.module.system.api.notify.dto.NotifySendResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/** Provider boundary for WeCom application messages; HTTP implementation is added by the config service. */
@Component
public class WecomNotifyChannelAdapter implements NotifyChannelAdapter {

    @Autowired(required = false)
    private NotifyChannelConfigService configService;

    @Override
    public String getChannelCode() {
        return NotifyChannelType.WECOM;
    }

    @Override
    public NotifySendResult send(NotifyDeliveryContext context) {
        if (configService == null || configService.getEnabled(context.getTenantId(), NotifyChannelType.WECOM) == null) {
            return NotifySendResult.failure("WECOM_DISABLED", "企业微信渠道未启用或未配置", false);
        }
        return NotifySendResult.failure("WECOM_ADAPTER_PENDING", "企业微信适配器尚未配置 HTTP 凭据", true);
    }
}
