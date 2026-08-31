package cn.iocoder.yudao.module.zsjos.service.media;

import cn.iocoder.yudao.module.system.api.notify.dto.NotifyBusinessEvent;
import cn.iocoder.yudao.module.system.api.notify.dto.NotifyRecipientDTO;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MediaNotifySceneProviderTest {

    private final MediaNotifySceneProvider provider = new MediaNotifySceneProvider();

    @Test
    void exposesPendingReturnAndFinalResultScenes() {
        Set<String> scenes = provider.getScenes().stream().map(scene -> scene.getCode()).collect(Collectors.toSet());

        assertTrue(scenes.containsAll(Set.of("media.content.pending_acceptance", "media.content.approved",
                "media.content.rejected", "media.ticket.pending_accept", "media.ticket.pending_check",
                "media.ticket.approved", "media.ticket.rejected", "media.positioning.operator_review",
                "media.positioning.student_confirmation", "media.positioning.student_confirmed",
                "media.positioning.student_rejected", "media.account.rebind_approved",
                "media.account.rebind_rejected", "media.account.maintenance_changed")));
    }

    @Test
    void resolvesEmployeeAndPartnerRecipientsFromResponsibilitySnapshot() {
        NotifyBusinessEvent event = NotifyBusinessEvent.builder().payload(Map.of(
                "assigneeUserId", 21L, "partnerAccountId", 31L)).build();

        assertEquals(Set.of(NotifyRecipientDTO.admin(21L), NotifyRecipientDTO.partner(31L)),
                provider.resolveRecipients(event, Set.of("assignee")));
    }
}
