package cn.iocoder.yudao.module.zsjos.service.payment;

import cn.iocoder.yudao.module.system.api.notify.dto.NotifyBusinessEvent;
import cn.iocoder.yudao.module.system.api.notify.dto.NotifyRecipientDTO;
import org.junit.jupiter.api.Test;
import java.util.Map;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

class PaymentNotifySceneProviderTest {
    private final PaymentNotifySceneProvider provider = new PaymentNotifySceneProvider();

    @Test
    void usesFrozenOwnerAndBusinessNumbersWithoutGuessingRecipients() {
        var event = NotifyBusinessEvent.builder().payload(Map.of("ownerUserId", 8L,
                "purchase.no", "PI-TEST", "payment.no", "PAY-TEST", "payment.amount", 100)).build();
        assertEquals(Set.of(NotifyRecipientDTO.admin(8L)), provider.resolveRecipients(event, Set.of("owner")));
        assertTrue(provider.resolveRecipients(event, Set.of("other")).isEmpty());
        assertTrue(provider.resolveRecipients(NotifyBusinessEvent.builder().build(), Set.of("owner")).isEmpty());
        assertEquals("PI-TEST", provider.resolveVariables(event, NotifyRecipientDTO.admin(8L)).get("purchase.no"));
        assertEquals(Set.of("message_detail", "none"), Set.copyOf(provider.getScenes().getFirst().getAllowedActions()));
    }
}
