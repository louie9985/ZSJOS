package cn.iocoder.yudao.module.zsjos.service.lead;

import cn.iocoder.yudao.module.system.api.dict.DictDataApi;
import cn.iocoder.yudao.module.system.api.notify.dto.NotifyBusinessEvent;
import cn.iocoder.yudao.module.system.api.notify.dto.NotifySceneRespDTO;
import cn.iocoder.yudao.module.system.api.notify.dto.NotifySceneVariableRespDTO;
import cn.iocoder.yudao.module.system.api.notify.dto.NotifyRecipientDTO;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import cn.iocoder.yudao.module.system.api.dept.DeptApi;
import cn.iocoder.yudao.module.system.api.dept.dto.DeptRespDTO;
import cn.iocoder.yudao.module.system.api.user.dto.AdminUserRespDTO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.LeadDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.lead.LeadAttachmentMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.lead.LeadIntendedProductMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.lead.LeadMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.lead.LeadAgingPoolCycleMapper;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.personnel.PartnerAccountDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.personnel.PartnerAccountMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static cn.iocoder.yudao.module.zsjos.enums.LeadConstants.*;
import static cn.iocoder.yudao.module.zsjos.enums.LeadNotifySceneConstants.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LeadNotifySceneProviderTest {
    @Mock private cn.iocoder.yudao.module.zsjos.dal.mysql.lead.LeadAssignmentHistoryMapper assignmentHistoryMapper;

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"sales_self_sourced", "education_self_sourced"})
    void sourceAssociationDistinguishesInapplicableAndBrokenSnapshots(String source) {
        var lead = new LeadDO().setId(1L).setSourceType(source);
        when(leadMapper.selectById(1L)).thenReturn(lead);
        var event = NotifyBusinessEvent.builder().sceneCode(CREATED).bizId(1L).operatorUserId(10L).build();
        var roles = Set.of(ROLE_NEW_MEDIA_PROVIDER);
        var missing = provider.evaluateRule(event, roles, Set.of());
        assertEquals("LEAD_SOURCE_SNAPSHOT_MISSING", missing.getErrorCode());
        assertFalse(missing.isSuccess()); assertFalse(missing.isRetryable());
        lead.setSourceProviderRecorded(true);
        assertEquals("LEAD_SOURCE_PROVIDER_NOT_SELECTED", provider.evaluateRule(event, roles, Set.of()).getErrorCode());
        assertTrue(provider.evaluateRule(event, roles, Set.of()).isSkipped());
        lead.setSourceProviderUserId(20L);
        assertEquals("LEAD_SOURCE_ATTRIBUTION_MISMATCH", provider.evaluateRule(event, roles, Set.of()).getErrorCode());
        lead.setProviderOwnerType(PROVIDER_OWNER_SYSTEM_USER); lead.setProviderOwnerId(20L);
        org.junit.jupiter.api.Assertions.assertNull(provider.evaluateRule(event, roles, Set.of()));
        assertEquals(Set.of(NotifyRecipientDTO.admin(20L)), provider.resolveRecipients(event, roles));
        lead.setSourceProviderUserId(10L); lead.setProviderOwnerId(10L);
        assertEquals("LEAD_SOURCE_PROVIDER_IS_OPERATOR", provider.evaluateRule(event, roles, Set.of()).getErrorCode());
    }

    @Test void sourceAssociationNeverSuppressesOtherRolesOrExplicitRecipients() {
        var event = NotifyBusinessEvent.builder().sceneCode(CREATED).bizId(1L).build();
        org.junit.jupiter.api.Assertions.assertNull(provider.evaluateRule(event, Set.of(ROLE_OPERATOR), Set.of()));
        org.junit.jupiter.api.Assertions.assertNull(provider.evaluateRule(event, Set.of(ROLE_NEW_MEDIA_PROVIDER, ROLE_OPERATOR), Set.of()));
        org.junit.jupiter.api.Assertions.assertNull(provider.evaluateRule(event, Set.of(ROLE_NEW_MEDIA_PROVIDER), Set.of(30L)));
        org.mockito.Mockito.verifyNoInteractions(leadMapper);
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"internal_new_media", "partner"})
    void sourceAssociationSkipsOrdinarySubmission(String source) {
        when(leadMapper.selectById(1L)).thenReturn(new LeadDO().setId(1L).setSourceType(source));
        var event = NotifyBusinessEvent.builder().sceneCode(CREATED).bizId(1L).build();
        var result = provider.evaluateRule(event, Set.of(ROLE_NEW_MEDIA_PROVIDER), Set.of());
        assertTrue(result.isSkipped()); assertFalse(result.isRetryable());
        assertEquals("LEAD_SOURCE_LINK_NOT_APPLICABLE", result.getErrorCode());
    }


    @Test void dispatchVariablesUseOriginalRoundAfterLeadChanges() {
        var lead = new LeadDO().setId(1L).setPendingAssigneeUserId(99L)
                .setPendingExpiresAt(java.time.LocalDateTime.now().plusHours(1));
        when(leadMapper.selectById(1L)).thenReturn(lead);
        var payload = new java.util.LinkedHashMap<String, Object>();
        payload.put("pendingSalesUserId", 10L); payload.put("assignment.historyId", 20L);
        payload.put("lead.pendingExpiresAt", null);
        var event = NotifyBusinessEvent.builder().tenantId(1L).sceneCode(ASSIGNED).bizId(1L).payload(payload).build();
        var values = provider.resolveVariables(event, NotifyRecipientDTO.admin(10L));
        org.junit.jupiter.api.Assertions.assertNull(values.get("lead.pendingExpiresAt"));
        assertEquals(20L, values.get("assignment.historyId"));
        org.mockito.Mockito.verify(adminUserApi).getUser(10L);
        org.mockito.Mockito.verify(adminUserApi, org.mockito.Mockito.never()).getUser(99L);
        org.junit.jupiter.api.Assertions.assertNull(provider.deliverySkipReason(
                NotifyBusinessEvent.builder().sceneCode(PUBLIC_POOL).build()));
    }

    @Test void dispatchDeliveryChecksCandidateRoundDeadlineAndTenant() {
        cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder.setTenantId(1L);
        try {
            var lead = new LeadDO().setId(1L).setAssignmentStatus(ASSIGNMENT_PENDING).setPendingAssigneeUserId(10L);
            when(leadMapper.selectById(1L)).thenReturn(lead);
            var history = new cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.LeadAssignmentHistoryDO();
            history.setId(20L); history.setCandidateUserId(10L);
            when(assignmentHistoryMapper.selectLatestDispatch(1L, false)).thenReturn(history);
            var event = NotifyBusinessEvent.builder().tenantId(1L).sceneCode(ASSIGNED).bizId(1L)
                    .payload(Map.of("pendingSalesUserId", 10L, "assignment.historyId", 20L)).build();
            org.junit.jupiter.api.Assertions.assertNull(provider.deliverySkipReason(event));
            history.setId(21L);
            assertEquals("LEAD_ASSIGNMENT_OBSOLETE", provider.deliverySkipReason(event));
            history.setId(20L); lead.setPendingExpiresAt(java.time.LocalDateTime.now().minusSeconds(1));
            assertEquals("LEAD_ASSIGNMENT_EXPIRED", provider.deliverySkipReason(event));
            lead.setPendingExpiresAt(null); lead.setPendingAssigneeUserId(11L);
            assertEquals("LEAD_ASSIGNMENT_OBSOLETE", provider.deliverySkipReason(event));
            lead.setPendingAssigneeUserId(10L); lead.setAssignmentStatus(ASSIGNMENT_OWNED);
            assertEquals("LEAD_ASSIGNMENT_OBSOLETE", provider.deliverySkipReason(event));
            when(leadMapper.selectById(1L)).thenReturn(null);
            assertEquals("LEAD_ASSIGNMENT_OBSOLETE", provider.deliverySkipReason(event));
            cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder.setTenantId(2L);
            org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class, () -> provider.deliverySkipReason(event));
        } finally { cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder.clear(); }
    }

    @Test
    void partnerNamesNeverResolveThroughSameIdEmployee() {
        LeadDO lead = new LeadDO().setId(1L).setPartnerId(70L).setSourceUserId(10L);
        when(leadMapper.selectById(1L)).thenReturn(lead);
        when(partnerAccountMapper.selectById(10L)).thenReturn(new PartnerAccountDO().setId(10L).setPartnerId(70L));
        var partner = new cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.PartnerDO();
        partner.setId(70L); partner.setName("合作方测试名称");
        when(partnerMapper.selectById(70L)).thenReturn(partner);
        NotifyBusinessEvent event = NotifyBusinessEvent.builder().sceneCode(CREATED).bizId(1L)
                .operatorUserId(10L).payload(Map.of("operatorUserType", 3)).build();
        Map<String, Object> values = provider.resolveVariables(event, NotifyRecipientDTO.partner(10L));
        assertEquals("合作方测试名称", values.get("operator.name"));
        assertEquals("合作方测试名称", values.get("submitter.name"));
        org.mockito.Mockito.verify(adminUserApi, org.mockito.Mockito.never()).getUser(10L);
    }

    @Test
    void partnerOperatorAndEmployeeOwnerWithSameIdRemainDifferentRecipients() {
        NotifyBusinessEvent event = NotifyBusinessEvent.builder().sceneCode(ACTIVATED)
                .bizId(1L).operatorUserId(10L)
                .payload(Map.of("operatorUserType", 3, "ownerUserId", 10L)).build();
        assertEquals(Set.of(NotifyRecipientDTO.partner(10L), NotifyRecipientDTO.admin(10L)),
                provider.resolveRecipients(event, Set.of(ROLE_OPERATOR, ROLE_OWNER)));
    }

    @Test
    void unknownOperatorTypeDoesNotBecomeEmployee() {
        NotifyBusinessEvent event = NotifyBusinessEvent.builder().sceneCode(CREATED)
                .bizId(1L).operatorUserId(10L).payload(Map.of("operatorUserType", 99)).build();
        assertEquals(Set.of(), provider.resolveRecipients(event, Set.of(ROLE_OPERATOR)));
    }

    @Test
    void activationNotifiesEducationOwnerWithoutSalesLeader() {
        when(leadMapper.selectById(1L)).thenReturn(new LeadDO().setOwnerIdentity(OWNER_EDUCATION));
        var event = NotifyBusinessEvent.builder().sceneCode(ACTIVATED).bizId(1L)
                .payload(Map.of("ownerUserId", 40L)).build();
        assertEquals(Set.of(NotifyRecipientDTO.admin(40L)),
                provider.resolveRecipients(event, Set.of(ROLE_OWNER, ROLE_DIRECT_LEADER)));
    }

    @Test
    void activationWithoutOwnerHasNoRecipients() {
        when(leadMapper.selectById(1L)).thenReturn(new LeadDO());
        var event = NotifyBusinessEvent.builder().sceneCode(ACTIVATED).bizId(1L).payload(Map.of()).build();
        assertTrue(provider.resolveRecipients(event, Set.of(ROLE_OWNER, ROLE_DIRECT_LEADER)).isEmpty());
    }

    @Test
    void feedbackUsesFrozenTypedRecipient() {
        var event = NotifyBusinessEvent.builder().sceneCode(cn.iocoder.yudao.module.zsjos.enums.LeadSubmitterFeedbackConstants.SCENE)
                .bizId(1L).payload(Map.of("feedback.recipientType", "PARTNER", "feedback.recipientId", 90L)).build();
        assertEquals(Set.of(NotifyRecipientDTO.partner(90L)), provider.resolveRecipients(event, Set.of("submitter")));
        event = NotifyBusinessEvent.builder().sceneCode(cn.iocoder.yudao.module.zsjos.enums.LeadSubmitterFeedbackConstants.SCENE)
                .bizId(1L).payload(Map.of("feedback.recipientType", "ADMIN", "feedback.recipientId", 90L)).build();
        assertEquals(Set.of(NotifyRecipientDTO.admin(90L)), provider.resolveRecipients(event, Set.of("submitter")));
        assertTrue(provider.resolveRecipients(event, Set.of("owner")).isEmpty());
    }

    @InjectMocks
    private LeadNotifySceneProvider provider;
    @Mock private LeadMapper leadMapper;
    @Mock private LeadIntendedProductMapper productMapper;
    @Mock private LeadAttachmentMapper attachmentMapper;
    @Mock private DictDataApi dictDataApi;
    @Mock private AdminUserApi adminUserApi;
    @Mock private PermissionApi permissionApi;
    @Mock private DeptApi deptApi;
    @Mock private LeadAgingPoolCycleMapper agingPoolCycleMapper;
    @Mock private LeadAssignmentService assignmentService;
    @Mock private PartnerAccountMapper partnerAccountMapper;
    @Mock private cn.iocoder.yudao.module.zsjos.dal.mysql.lead.PartnerMapper partnerMapper;

    @Test
    void registersAllScenesWithSceneSpecificVariables() {
        List<NotifySceneRespDTO> scenes = provider.getScenes();

        assertEquals(44, scenes.size());
        assertEquals(44, scenes.stream().map(NotifySceneRespDTO::getCode).distinct().count());
        assertTrue(variableKeys(scene(scenes, ASSIGNED)).contains("lead.no"));
        assertFalse(variableKeys(scene(scenes, ASSIGNED)).contains("lead.name"));
        assertTrue(variableKeys(scene(scenes, ASSIGNED)).contains("assignment.attempt"));
        assertFalse(variableKeys(scene(scenes, ASSIGNED)).contains("followUp.result"));
        assertTrue(variableKeys(scene(scenes, FOLLOW_UP_RECORDED)).contains("followUp.result"));
        assertFalse(variableKeys(scene(scenes, FOLLOW_UP_RECORDED)).contains("assignment.attempt"));
        assertTrue(variableKeys(scene(scenes, APPEAL_SUBMITTED)).contains("appeal.roundNo"));
        assertTrue(variableKeys(scene(scenes, SUBMITTER_URGED)).contains("urge.reason"));
        assertTrue(variableKeys(scene(scenes, SUBMITTER_ASSIST_REQUESTED)).contains("assist.problem"));
        assertTrue(scenes.stream().anyMatch(item -> COMPLAINT_FOUNDED.equals(item.getCode())));
        assertTrue(variableKeys(scene(scenes, COMPLAINT_UNFOUNDED)).contains("complaint.handlerOpinion"));
    }

    @Test
    void resolvesAppealReviewersFromEventPayload() {
        NotifyBusinessEvent event = NotifyBusinessEvent.builder().sceneCode(APPEAL_SUBMITTED)
                .payload(Map.of("appeal.reviewerUserIds", List.of(31L, 32L))).build();
        assertEquals(Set.of(NotifyRecipientDTO.admin(31L), NotifyRecipientDTO.admin(32L)),
                provider.resolveRecipients(event, Set.of(ROLE_APPEAL_REVIEWERS)));
    }

    @Test
    void resolvesLeadCreatedProviderAndActualOperator() {
        NotifyBusinessEvent event = NotifyBusinessEvent.builder().sceneCode(CREATED)
                .bizId(1L).operatorUserId(10L).payload(Map.of("submitterUserId", 20L)).build();
        when(leadMapper.selectById(1L)).thenReturn(new LeadDO().setProviderOwnerType("system_user")
                .setProviderOwnerId(20L));

        assertEquals(Set.of(NotifyRecipientDTO.admin(10L), NotifyRecipientDTO.admin(20L)),
                provider.resolveRecipients(event, Set.of(ROLE_SUBMITTER, ROLE_OPERATOR)));
    }

    @Test
    void resolvesDedicatedProviderOnlyForSalesSelfSourcedFrozenProvider() {
        when(leadMapper.selectById(1L)).thenReturn(new LeadDO().setSourceType(SOURCE_SALES_SELF)
                .setSourceProviderRecorded(true).setSourceProviderUserId(20L)
                .setProviderOwnerType(PROVIDER_OWNER_SYSTEM_USER).setProviderOwnerId(20L));
        NotifyBusinessEvent event = NotifyBusinessEvent.builder().sceneCode(CREATED)
                .bizId(1L).operatorUserId(10L).payload(Map.of("newMediaProviderUserId", 99L)).build();

        assertEquals(Set.of(NotifyRecipientDTO.admin(20L)),
                provider.resolveRecipients(event, Set.of(ROLE_NEW_MEDIA_PROVIDER)));
    }

    @Test
    void doesNotResolveDedicatedProviderForOtherSubmissionKindsOrInvalidFrozenAttribution() {
        when(leadMapper.selectById(1L)).thenReturn(new LeadDO().setSourceType(SOURCE_INTERNAL_NEW_MEDIA)
                .setProviderOwnerType(PROVIDER_OWNER_SYSTEM_USER).setProviderOwnerId(20L));
        when(leadMapper.selectById(2L)).thenReturn(new LeadDO().setSourceType(SOURCE_SALES_SELF)
                .setSourceProviderRecorded(true).setProviderOwnerType(PROVIDER_OWNER_SYSTEM_USER).setProviderOwnerId(10L));
        when(leadMapper.selectById(3L)).thenReturn(new LeadDO().setSourceType(SOURCE_SALES_SELF)
                .setSourceProviderRecorded(true).setSourceProviderUserId(20L)
                .setProviderOwnerType(PROVIDER_OWNER_SYSTEM_USER).setProviderOwnerId(21L));
        when(leadMapper.selectById(4L)).thenReturn(new LeadDO().setSourceType(SOURCE_SALES_SELF)
                .setSourceProviderRecorded(false).setSourceProviderUserId(20L)
                .setProviderOwnerType(PROVIDER_OWNER_SYSTEM_USER).setProviderOwnerId(20L));
        when(leadMapper.selectById(5L)).thenReturn(new LeadDO().setSourceType(SOURCE_SALES_SELF)
                .setSourceProviderRecorded(true).setSourceProviderUserId(10L)
                .setProviderOwnerType(PROVIDER_OWNER_SYSTEM_USER).setProviderOwnerId(10L));

        for (long leadId = 1L; leadId <= 5L; leadId++) {
            NotifyBusinessEvent event = NotifyBusinessEvent.builder().sceneCode(CREATED)
                    .bizId(leadId).operatorUserId(10L).build();
            assertEquals(Set.of(), provider.resolveRecipients(event, Set.of(ROLE_NEW_MEDIA_PROVIDER)));
        }
    }

    @Test
    void resolvesActualAdminAndPartnerComplainants() {
        PartnerAccountDO partnerAccount = new PartnerAccountDO();
        partnerAccount.setId(71L); partnerAccount.setPartnerId(70L);
        when(partnerAccountMapper.selectByPartnerId(70L)).thenReturn(partnerAccount);

        NotifyBusinessEvent adminComplaint = NotifyBusinessEvent.builder().sceneCode(COMPLAINT_FOUNDED)
                .payload(Map.of("complaint.complainantUserId", 10L)).build();
        NotifyBusinessEvent partnerComplaint = NotifyBusinessEvent.builder().sceneCode(COMPLAINT_UNFOUNDED)
                .payload(Map.of("complaint.partnerId", 70L)).build();

        assertEquals(Set.of(NotifyRecipientDTO.admin(10L)),
                provider.resolveRecipients(adminComplaint, Set.of(ROLE_COMPLAINANT)));
        assertEquals(Set.of(NotifyRecipientDTO.partner(71L)),
                provider.resolveRecipients(partnerComplaint, Set.of(ROLE_COMPLAINANT)));
    }

    @Test
    void resolvesPartnerSubmitterAndOwningEmployeeSeparately() {
        PartnerAccountDO account = new PartnerAccountDO();
        account.setId(71L); account.setPartnerId(70L);
        when(leadMapper.selectById(1L)).thenReturn(new LeadDO().setProviderOwnerType(PROVIDER_OWNER_PARTNER)
                .setProviderOwnerId(70L));
        when(partnerAccountMapper.selectByPartnerId(70L)).thenReturn(account);
        NotifyBusinessEvent event = NotifyBusinessEvent.builder().bizId(1L)
                .payload(Map.of("partnerOwnerUserId", 30L)).build();

        assertEquals(Set.of(NotifyRecipientDTO.partner(71L)),
                provider.resolveRecipients(event, Set.of(ROLE_SUBMITTER)));
        assertEquals(Set.of(), provider.resolveRecipients(event, Set.of(ROLE_NEW_MEDIA_PROVIDER)));
        assertEquals(Set.of(NotifyRecipientDTO.admin(30L)),
                provider.resolveRecipients(event, Set.of(ROLE_PARTNER_OWNER)));
    }

    @Test
    void resolvesQualificationManagersFromOwnerDepartmentHierarchy() {
        AdminUserRespDTO owner = new AdminUserRespDTO();
        owner.setId(20L); owner.setDeptId(200L);
        DeptRespDTO child = new DeptRespDTO();
        child.setId(200L); child.setParentId(100L); child.setLeaderUserId(21L);
        DeptRespDTO parent = new DeptRespDTO();
        parent.setId(100L); parent.setLeaderUserId(22L);
        when(adminUserApi.getUser(20L)).thenReturn(owner);
        when(deptApi.getDept(200L)).thenReturn(child);
        when(deptApi.getDept(100L)).thenReturn(parent);
        NotifyBusinessEvent event = NotifyBusinessEvent.builder().sceneCode(QUALIFICATION_SUSPENDED)
                .payload(Map.of("ownerUserId", 20L)).build();

        Set<NotifyRecipientDTO> recipients = provider.resolveRecipients(event, Set.of(ROLE_OWNER, ROLE_QUALIFICATION_MANAGERS));

        assertEquals(Set.of(NotifyRecipientDTO.admin(20L), NotifyRecipientDTO.admin(21L),
                NotifyRecipientDTO.admin(22L)), recipients);
    }

    @Test
    void resolvesContactValuesAccordingToRecipientPermission() {
        LeadDO lead = new LeadDO();
        lead.setId(1L);
        lead.setLeadNo("KZ202608160000000001");
        lead.setSourceUserId(10L);
        lead.setProviderOwnerType("system_user"); lead.setProviderOwnerId(10L);
        lead.setOwnerUserId(20L);
        lead.setSubmittedName("张三丰");
        lead.setSubmittedMobile("13800138000");
        lead.setSubmittedWechatId("wechat-full");
        when(leadMapper.selectById(1L)).thenReturn(lead);
        when(productMapper.selectListByLeadId(1L)).thenReturn(List.of());
        when(attachmentMapper.selectListByLeadId(1L)).thenReturn(List.of());
        NotifyBusinessEvent event = NotifyBusinessEvent.builder().sceneCode(ASSIGNED).bizId(1L).build();
        Map<String, Object> masked = provider.resolveVariables(event, NotifyRecipientDTO.admin(30L));
        Map<String, Object> full = provider.resolveVariables(event, NotifyRecipientDTO.admin(10L));

        assertEquals("KZ202608160000000001", full.get("lead.no"));
        assertEquals(1L, full.get("lead.id"));
        assertFalse("13800138000".equals(masked.get("lead.mobile")));
        assertFalse("wechat-full".equals(masked.get("lead.wechatId")));
        assertEquals("13800138000", full.get("lead.mobile"));
        assertEquals("wechat-full", full.get("lead.wechatId"));
    }

    @Test
    void blindsCounterpartIdentityForSubmitterAndOwnerNotifications() {
        LeadDO lead = new LeadDO();
        lead.setId(1L); lead.setSourceUserId(10L); lead.setOwnerUserId(20L);
        lead.setProviderOwnerType("system_user"); lead.setProviderOwnerId(10L);
        lead.setAssignmentStatus("owned"); lead.setSubmittedName("张三丰");
        when(leadMapper.selectById(1L)).thenReturn(lead);
        when(productMapper.selectListByLeadId(1L)).thenReturn(List.of());
        when(attachmentMapper.selectListByLeadId(1L)).thenReturn(List.of());
        AdminUserRespDTO submitter = new AdminUserRespDTO(); submitter.setId(10L); submitter.setNickname("提交销售");
        AdminUserRespDTO owner = new AdminUserRespDTO(); owner.setId(20L); owner.setNickname("负责销售");
        when(adminUserApi.getUser(10L)).thenReturn(submitter);
        when(adminUserApi.getUser(20L)).thenReturn(owner);
        NotifyBusinessEvent event = NotifyBusinessEvent.builder().sceneCode(ASSIGNED).bizId(1L).build();

        Map<String, Object> submitterValues = provider.resolveVariables(event, NotifyRecipientDTO.admin(10L));
        Map<String, Object> ownerValues = provider.resolveVariables(event, NotifyRecipientDTO.admin(20L));

        assertNotEquals("负责销售", submitterValues.get("owner.name"));
        assertEquals(null, submitterValues.get("owner.id"));
        assertNotEquals("提交销售", ownerValues.get("submitter.name"));
        assertEquals(null, ownerValues.get("submitter.id"));
    }

    @Test
    void keepsCounterpartIdentityForQueryAllNotificationRecipient() {
        LeadDO lead = new LeadDO();
        lead.setId(1L); lead.setSourceUserId(10L); lead.setOwnerUserId(20L); lead.setAssignmentStatus("owned");
        lead.setProviderOwnerType("system_user"); lead.setProviderOwnerId(10L);
        when(leadMapper.selectById(1L)).thenReturn(lead);
        when(productMapper.selectListByLeadId(1L)).thenReturn(List.of());
        when(attachmentMapper.selectListByLeadId(1L)).thenReturn(List.of());
        AdminUserRespDTO submitter = new AdminUserRespDTO(); submitter.setId(10L); submitter.setNickname("提交销售");
        AdminUserRespDTO owner = new AdminUserRespDTO(); owner.setId(20L); owner.setNickname("负责销售");
        when(adminUserApi.getUser(10L)).thenReturn(submitter);
        when(adminUserApi.getUser(20L)).thenReturn(owner);
        when(permissionApi.hasAnyPermissions(30L, "zsjos:lead:query-all")).thenReturn(true);

        Map<String, Object> values = provider.resolveVariables(
                NotifyBusinessEvent.builder().sceneCode(ASSIGNED).bizId(1L).build(), NotifyRecipientDTO.admin(30L));

        assertEquals("提交销售", values.get("submitter.name"));
        assertEquals("负责销售", values.get("owner.name"));
        assertEquals(10L, values.get("submitter.id"));
    }

    private static NotifySceneRespDTO scene(List<NotifySceneRespDTO> scenes, String code) {
        return scenes.stream().filter(item -> code.equals(item.getCode())).findFirst().orElseThrow();
    }

    private static List<String> variableKeys(NotifySceneRespDTO scene) {
        return scene.getVariables().stream().map(NotifySceneVariableRespDTO::getKey).toList();
    }
}
