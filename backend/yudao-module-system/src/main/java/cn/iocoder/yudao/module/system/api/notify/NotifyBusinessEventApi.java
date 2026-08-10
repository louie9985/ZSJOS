package cn.iocoder.yudao.module.system.api.notify;

import cn.iocoder.yudao.module.system.api.notify.dto.NotifyBusinessEvent;

public interface NotifyBusinessEventApi {

    void publish(NotifyBusinessEvent event);
}
