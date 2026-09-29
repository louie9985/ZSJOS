package cn.iocoder.yudao.module.system.api.notify;

import cn.iocoder.yudao.module.system.api.notify.dto.NotifyDeliveryPageDTO;
import cn.iocoder.yudao.module.system.api.notify.dto.NotifyRecipientDTO;
import cn.iocoder.yudao.module.system.service.notify.NotifyFixedDeliveryQueryService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
public class NotifyDeliveryApiImpl implements NotifyDeliveryApi {
    @Resource private NotifyFixedDeliveryQueryService service;
    @Override public NotifyDeliveryPageDTO queryFixedEvent(String sceneCode, String sourceEventKey,
            List<NotifyRecipientDTO> recipients, long afterOutboxId, int limit) {
        return service.query(sceneCode, sourceEventKey, recipients, afterOutboxId, limit);
    }
}
