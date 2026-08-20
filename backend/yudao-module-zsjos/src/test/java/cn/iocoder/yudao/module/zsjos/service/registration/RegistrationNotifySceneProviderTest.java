package cn.iocoder.yudao.module.zsjos.service.registration;

import cn.iocoder.yudao.module.system.api.notify.dto.NotifyBusinessEvent;
import cn.iocoder.yudao.module.system.api.notify.dto.NotifyRecipientDTO;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.Set;

import static cn.iocoder.yudao.module.zsjos.service.registration.RegistrationConstants.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RegistrationNotifySceneProviderTest {

    @InjectMocks private RegistrationNotifySceneProvider provider;
    @Mock private PermissionApi permissionApi;

    @Test
    void resolvesAllEnabledPoolHandlersWithPermission() {
        when(permissionApi.getEnabledUserIdsByPermission(PERMISSION_QUERY_POOL)).thenReturn(Set.of(11L, 12L));
        NotifyBusinessEvent event = NotifyBusinessEvent.builder().tenantId(1L).bizId(7L).payload(Map.of()).build();

        Set<NotifyRecipientDTO> recipients = provider.resolveRecipients(event, Set.of(NOTIFY_ROLE_POOL_HANDLERS));

        assertEquals(Set.of(NotifyRecipientDTO.admin(11L), NotifyRecipientDTO.admin(12L)), recipients);
    }

    @Test
    void resolvesPlannerRecipientFromEventPayload() {
        NotifyBusinessEvent event = NotifyBusinessEvent.builder().tenantId(1L).bizId(7L)
                .payload(Map.of("studyPlannerUserId", 241L)).build();

        assertEquals(Set.of(NotifyRecipientDTO.admin(241L)),
                provider.resolveRecipients(event, Set.of(NOTIFY_ROLE_STUDY_PLANNER)));
    }

    @Test
    void resolvesContentDirectorRecipientFromEventPayload() {
        NotifyBusinessEvent event = NotifyBusinessEvent.builder().tenantId(1L).bizId(7L)
                .payload(Map.of("contentDirectorUserId", 301L)).build();

        assertEquals(Set.of(NotifyRecipientDTO.admin(301L)),
                provider.resolveRecipients(event, Set.of(NOTIFY_ROLE_CONTENT_DIRECTOR)));
    }

    @Test
    void registrationScenesUseBusinessNumbersInsteadOfStudentName() {
        provider.getScenes().forEach(scene -> {
            Set<String> keys = scene.getVariables().stream()
                    .map(variable -> variable.getKey()).collect(java.util.stream.Collectors.toSet());
            org.junit.jupiter.api.Assertions.assertFalse(keys.contains("student.name"));
            org.junit.jupiter.api.Assertions.assertTrue(keys.contains("order.no"));
        });
    }

    @Test
    void toleratesMissingPayload() {
        NotifyBusinessEvent event = NotifyBusinessEvent.builder().tenantId(1L).bizId(7L).build();

        assertEquals(Set.of(), provider.resolveRecipients(event, Set.of(NOTIFY_ROLE_STUDY_PLANNER)));
        Map<String, Object> variables = provider.resolveVariables(event, NotifyRecipientDTO.admin(1L));
        assertEquals(null, variables.get("order.no"));
        assertEquals(null, variables.get("lead.no"));
    }
}
