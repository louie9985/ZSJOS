package cn.iocoder.yudao.module.system.api.notify;

import cn.iocoder.yudao.module.system.api.notify.dto.NotifyDeliveryContext;

/** Creates short-ticket click-back URLs for WeCom application messages. */
public interface NotifyWecomClickUrlProvider {

    String createClickUrl(NotifyDeliveryContext context);
}
