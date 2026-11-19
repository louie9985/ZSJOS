package cn.iocoder.yudao.module.zsjos.service.registration;

import cn.iocoder.yudao.module.system.api.notify.dto.NotifyBusinessEvent;
import cn.iocoder.yudao.module.system.api.notify.dto.NotifyRecipientDTO;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import cn.iocoder.yudao.module.system.api.user.dto.AdminUserRespDTO;
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
    @Mock private AdminUserApi adminUserApi;

    @Mock private cn.iocoder.yudao.module.zsjos.dal.mysql.registration.RegistrationCaseMapper cases;

    @Test
    void resolvesCurrentFulfillmentOwner() {
        var item = new cn.iocoder.yudao.module.zsjos.dal.dataobject.registration.RegistrationCaseDO();
        item.setOwnerUserId(11L);
        when(cases.selectById(7L)).thenReturn(item);
        when(adminUserApi.getUser(11L)).thenReturn(user(11L));
        when(permissionApi.hasAnyPermissions(11L, "zsjos:registration:query-my")).thenReturn(true);
        var event = NotifyBusinessEvent.builder().sceneCode(NOTIFY_SCENE_TASK_CREATED).tenantId(1L).bizId(7L).build();
        assertEquals(Set.of(NotifyRecipientDTO.admin(11L)),
                provider.resolveRecipients(event, Set.of(NOTIFY_ROLE_POOL_HANDLERS)));

        item.setOwnerUserId(12L);
        when(adminUserApi.getUser(12L)).thenReturn(user(12L));
        when(permissionApi.hasAnyPermissions(12L, "zsjos:registration:query-my")).thenReturn(true);
        assertEquals(Set.of(NotifyRecipientDTO.admin(12L)),
                provider.resolveRecipients(event, Set.of(NOTIFY_ROLE_POOL_HANDLERS)));
    }

    @Test
    void resolvesPlannerRecipientFromEventPayload() {
        NotifyBusinessEvent event = NotifyBusinessEvent.builder().tenantId(1L).bizId(7L)
                .payload(Map.of("studyPlannerUserId", 241L)).build();

        assertEquals(Set.of(NotifyRecipientDTO.admin(241L)),
                provider.resolveRecipients(event, Set.of(NOTIFY_ROLE_HEAD_TEACHER)));
    }

    @Test
    void resolvesContentDirectorRecipientFromEventPayload() {
        NotifyBusinessEvent event = NotifyBusinessEvent.builder().tenantId(1L).bizId(7L)
                .payload(Map.of("contentDirectorUserId", 301L)).build();

        assertEquals(Set.of(NotifyRecipientDTO.admin(301L)),
                provider.resolveRecipients(event, Set.of(NOTIFY_ROLE_CONTENT_DIRECTOR)));
    }

    @Test
    void plannerSceneUsesStudentIdentityAndNumber() {
        var scene = provider.getScenes().stream()
                .filter(item -> NOTIFY_SCENE_PLANNER_ASSIGNED.equals(item.getCode())).findFirst().orElseThrow();
        Set<String> keys = scene.getVariables().stream()
                .map(variable -> variable.getKey()).collect(java.util.stream.Collectors.toSet());
        org.junit.jupiter.api.Assertions.assertTrue(keys.contains("student.name"));
        org.junit.jupiter.api.Assertions.assertTrue(keys.contains("student.no"));
        org.junit.jupiter.api.Assertions.assertFalse(keys.contains("lead.no"));
        org.junit.jupiter.api.Assertions.assertTrue(keys.contains("order.no"));
    }

    @Test
    void toleratesMissingPayload() {
        NotifyBusinessEvent event = NotifyBusinessEvent.builder().tenantId(1L).bizId(7L).build();

        assertEquals(Set.of(), provider.resolveRecipients(event, Set.of(NOTIFY_ROLE_HEAD_TEACHER)));
        Map<String, Object> variables = provider.resolveVariables(event, NotifyRecipientDTO.admin(1L));
        assertEquals(null, variables.get("order.no"));
        assertEquals(null, variables.get("student.no"));
    }

    @Test
    void resolvesStudentNumberForPlannerTemplate() {
        NotifyBusinessEvent event = NotifyBusinessEvent.builder().tenantId(1L).bizId(7L)
                .payload(Map.of("registrationCaseId", 7L, "studentNo", "XS202608210001"))
                .build();

        Map<String, Object> variables = provider.resolveVariables(event, NotifyRecipientDTO.admin(241L));

        assertEquals("XS202608210001", variables.get("student.no"));
    }

    private static AdminUserRespDTO user(Long id) {
        return new AdminUserRespDTO().setId(id).setStatus(0);
    }
}
