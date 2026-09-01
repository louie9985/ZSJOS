package cn.iocoder.yudao.module.system.api.notify;

import cn.iocoder.yudao.module.system.api.notify.dto.NotifyBusinessEvent;
import cn.iocoder.yudao.module.system.api.notify.dto.NotifySendResult;

public interface NotifyBusinessEventApi {

    void publish(NotifyBusinessEvent event);

    /**
     * Synchronously confirms that all deliveries selected by the event are persisted or sent.
     * Existing asynchronous publishers should continue to use {@link #publish(NotifyBusinessEvent)}.
     */
    NotifySendResult publishConfirmed(NotifyBusinessEvent event);
}
