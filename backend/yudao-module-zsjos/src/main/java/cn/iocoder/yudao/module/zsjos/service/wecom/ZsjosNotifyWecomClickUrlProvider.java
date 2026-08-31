package cn.iocoder.yudao.module.zsjos.service.wecom;

import cn.iocoder.yudao.module.system.api.notify.NotifyWecomClickUrlProvider;
import cn.iocoder.yudao.module.system.api.notify.dto.NotifyDeliveryContext;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

@Component
public class ZsjosNotifyWecomClickUrlProvider implements NotifyWecomClickUrlProvider {

    @Resource
    private WecomClickTicketService wecomClickTicketService;

    @Override
    public String createClickUrl(NotifyDeliveryContext context) {
        return wecomClickTicketService.createClickUrl(context);
    }
}
