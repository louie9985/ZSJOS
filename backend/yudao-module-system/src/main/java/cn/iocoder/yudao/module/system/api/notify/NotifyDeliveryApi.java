package cn.iocoder.yudao.module.system.api.notify;

import cn.iocoder.yudao.module.system.api.notify.dto.NotifyDeliveryPageDTO;
import cn.iocoder.yudao.module.system.api.notify.dto.NotifyRecipientDTO;
import java.util.List;

/** Internal business API. Caller authorizes its own business object; tenant comes only from context. */
public interface NotifyDeliveryApi {
    /** Cursor pages rules, while recipients is a bounded slice of the caller's persisted fixed roster.
     * Missing/expired evidence is never interpreted as success. This method does not send or retry.
     */
    NotifyDeliveryPageDTO queryFixedEvent(String sceneCode, String sourceEventKey,
                                         List<NotifyRecipientDTO> recipients, long afterOutboxId, int limit);
}
