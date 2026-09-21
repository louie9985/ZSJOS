package cn.iocoder.yudao.module.system.api.notify;

import cn.iocoder.yudao.module.system.api.notify.dto.NotifyBusinessEvent;
import cn.iocoder.yudao.module.system.api.notify.dto.NotifySendResult;

public interface NotifyBusinessEventApi {

    void publish(NotifyBusinessEvent event);

    /**
     * Confirms persistence or sending for the selected deliveries. WeCom confirms durable outbox
     * acceptance (WECOM_QUEUED), not provider delivery; inspect delivery status for the final result.
     * Existing asynchronous publishers should continue to use {@link #publish(NotifyBusinessEvent)}.
     */
    NotifySendResult publishConfirmed(NotifyBusinessEvent event);
}
