package cn.iocoder.yudao.module.zsjos.service.registration;

import cn.iocoder.yudao.module.system.api.notify.dto.NotifyBusinessEvent;
import cn.iocoder.yudao.module.system.api.notify.dto.NotifyRecipientDTO;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import cn.iocoder.yudao.module.system.api.dept.DeptApi;
import cn.iocoder.yudao.module.system.api.dept.dto.DeptRespDTO;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import cn.iocoder.yudao.module.system.api.user.dto.AdminUserRespDTO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.order.SalesOrderApprovalConfigDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.order.SalesOrderApprovalConfigMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.List;
import java.util.Set;

import static cn.iocoder.yudao.module.zsjos.service.registration.RegistrationConstants.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RegistrationNotifySceneProviderTest {

    @InjectMocks private RegistrationNotifySceneProvider provider;
    @Mock private PermissionApi permissionApi;
    @Mock private SalesOrderApprovalConfigMapper approvalConfigMapper;
    @Mock private DeptApi deptApi;
    @Mock private AdminUserApi adminUserApi;

    @Test
    void resolvesAllEnabledPoolHandlersWithPermission() {
        when(permissionApi.getEnabledUserIdsByPermission(PERMISSION_QUERY_POOL)).thenReturn(Set.of(11L, 12L));
        when(approvalConfigMapper.selectCurrent()).thenReturn(new SalesOrderApprovalConfigDO().setRegistrationDeptId(1030L));
        when(deptApi.getChildDeptList(1030L)).thenReturn(List.of(new DeptRespDTO().setId(1031L)));
        when(adminUserApi.getUserListByDeptIds(Set.of(1030L, 1031L))).thenReturn(List.of(user(11L), user(12L)));
        NotifyBusinessEvent event = NotifyBusinessEvent.builder().tenantId(1L).bizId(7L).payload(Map.of()).build();

        Set<NotifyRecipientDTO> recipients = provider.resolveRecipients(event, Set.of(NOTIFY_ROLE_POOL_HANDLERS));

        assertEquals(Set.of(NotifyRecipientDTO.admin(11L), NotifyRecipientDTO.admin(12L)), recipients);
    }

    @Test
    void resolvesOnlyPermissionUsersInsideConfiguredDepartmentSubtree() {
        when(permissionApi.getEnabledUserIdsByPermission(PERMISSION_QUERY_POOL)).thenReturn(Set.of(11L, 12L, 13L));
        when(approvalConfigMapper.selectCurrent()).thenReturn(new SalesOrderApprovalConfigDO().setRegistrationDeptId(1030L));
        when(deptApi.getChildDeptList(1030L)).thenReturn(List.of(new DeptRespDTO().setId(1031L)));
        when(adminUserApi.getUserListByDeptIds(Set.of(1030L, 1031L)))
                .thenReturn(List.of(user(11L)));

        Set<NotifyRecipientDTO> recipients = provider.resolveRecipients(
                NotifyBusinessEvent.builder().tenantId(1L).bizId(7L).build(), Set.of(NOTIFY_ROLE_POOL_HANDLERS));

        assertEquals(Set.of(NotifyRecipientDTO.admin(11L)), recipients);
    }

    @Test
    void sendsNobodyWhenRegistrationDepartmentIsNotConfigured() {
        when(permissionApi.getEnabledUserIdsByPermission(PERMISSION_QUERY_POOL)).thenReturn(Set.of(11L));
        when(approvalConfigMapper.selectCurrent()).thenReturn(new SalesOrderApprovalConfigDO());

        assertEquals(Set.of(), provider.resolveRecipients(
                NotifyBusinessEvent.builder().tenantId(1L).bizId(7L).build(), Set.of(NOTIFY_ROLE_POOL_HANDLERS)));
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

        assertEquals(Set.of(), provider.resolveRecipients(event, Set.of(NOTIFY_ROLE_STUDY_PLANNER)));
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
        return new AdminUserRespDTO().setId(id);
    }
}
