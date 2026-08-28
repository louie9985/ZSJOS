package cn.iocoder.yudao.module.zsjos.service.feedback;

import cn.iocoder.yudao.module.system.api.notify.dto.NotifyBusinessEvent;
import cn.iocoder.yudao.module.system.api.notify.dto.NotifyRecipientDTO;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FeedbackNotifySceneProviderTest {

    private final FeedbackNotifySceneProvider provider = new FeedbackNotifySceneProvider();

    @Test
    void readySceneIsRegisteredForDispatchers() {
        assertTrue(provider.getScenes().stream().anyMatch(scene ->
                FeedbackConstants.NOTIFY_SCENE_READY_FOR_HANDLING.equals(scene.getCode())
                        && scene.getRecipientRoles().stream()
                        .anyMatch(role -> "dispatcher".equals(role.getCode()))));
    }

    @Test
    void readyFeedbackTargetsAllConfiguredDispatchersButNotSubmitter() {
        Set<NotifyRecipientDTO> recipients = provider.resolveRecipients(event(Map.of(
                "submitterUserId", 11L,
                "dispatcherUserIds", List.of(21L, 22L))), Set.of("dispatcher"));

        assertEquals(Set.of(NotifyRecipientDTO.admin(21L), NotifyRecipientDTO.admin(22L)), recipients);
    }

    @Test
    void readyFeedbackWithoutDispatchersHasNoRecipients() {
        Set<NotifyRecipientDTO> recipients = provider.resolveRecipients(event(Map.of(
                "submitterUserId", 11L,
                "dispatcherUserIds", List.of())), Set.of("dispatcher"));

        assertEquals(Set.of(), recipients);
    }

    @Test
    void employeeReplyTargetsAssigneeWhenPresent() {
        Set<NotifyRecipientDTO> recipients = provider.resolveRecipients(event(Map.of(
                "assigneeUserId", 21L,
                "dispatcherUserIds", List.of(31L, 32L))), Set.of("handler"));

        assertEquals(Set.of(NotifyRecipientDTO.admin(21L)), recipients);
    }

    @Test
    void employeeReplyFallsBackToConfiguredDispatchers() {
        Set<NotifyRecipientDTO> recipients = provider.resolveRecipients(event(Map.of(
                "dispatcherUserIds", List.of(31L, 32L))), Set.of("handler"));

        assertEquals(Set.of(NotifyRecipientDTO.admin(31L), NotifyRecipientDTO.admin(32L)), recipients);
    }

    private NotifyBusinessEvent event(Map<String, Object> payload) {
        return NotifyBusinessEvent.builder().bizType("feedback").bizId(1L).payload(payload).build();
    }
}
