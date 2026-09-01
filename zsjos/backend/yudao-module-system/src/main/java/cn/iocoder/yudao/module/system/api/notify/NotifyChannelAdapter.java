package cn.iocoder.yudao.module.system.api.notify;

import cn.iocoder.yudao.module.system.api.notify.dto.NotifyDeliveryContext;
import cn.iocoder.yudao.module.system.api.notify.dto.NotifySendResult;

/** Strategy boundary for channels owned by the message center. */
public interface NotifyChannelAdapter {

    String getChannelCode();

    NotifySendResult send(NotifyDeliveryContext context);
}
