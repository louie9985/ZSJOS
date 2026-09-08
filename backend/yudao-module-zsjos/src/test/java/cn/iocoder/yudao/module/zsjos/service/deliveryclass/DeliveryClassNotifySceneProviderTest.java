package cn.iocoder.yudao.module.zsjos.service.deliveryclass;

import cn.iocoder.yudao.module.system.api.notify.dto.NotifyBusinessEvent;
import cn.iocoder.yudao.module.system.api.notify.dto.NotifyRecipientDTO;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DeliveryClassNotifySceneProviderTest {
    private final DeliveryClassNotifySceneProvider provider = new DeliveryClassNotifySceneProvider();

    @Test
    void resolvesOldAndNewOwnersAndDeduplicatesRecipients() {
        NotifyBusinessEvent event = NotifyBusinessEvent.builder().payload(Map.of(
                "previousOwnerUserId", 11L, "newOwnerUserId", 20L,
                "serviceRelationId", 10L, "classId", 200L, "reason", "调班")).build();

        assertEquals(Set.of(NotifyRecipientDTO.admin(11L), NotifyRecipientDTO.admin(20L)),
                provider.resolveRecipients(event, Set.of("previous_owner", "new_owner")));
        assertEquals("调班", provider.resolveVariables(event, NotifyRecipientDTO.admin(20L))
                .get("transfer.reason"));
    }

    @Test
    void pendingSourceNotifiesOnlyNewOwner() {
        NotifyBusinessEvent event = NotifyBusinessEvent.builder()
                .payload(Map.of("newOwnerUserId", 20L)).build();

        assertEquals(Set.of(NotifyRecipientDTO.admin(20L)),
                provider.resolveRecipients(event, Set.of("previous_owner", "new_owner")));
    }
}
