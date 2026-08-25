package cn.iocoder.yudao.module.zsjos.service.positioning;

import cn.iocoder.yudao.module.bpm.api.task.BpmProcessInstanceApi;
import cn.iocoder.yudao.module.bpm.api.task.dto.BpmProcessInstanceCreateReqDTO;
import cn.iocoder.yudao.module.system.api.dept.PostApi;
import cn.iocoder.yudao.module.system.api.dept.dto.PostRespDTO;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import cn.iocoder.yudao.module.system.api.user.dto.AdminUserRespDTO;
import cn.iocoder.yudao.module.zsjos.controller.admin.positioning.vo.PositioningCardSaveReqVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.positioning.vo.PositioningCardDraftRespVO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.positioning.PositioningCardDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.positioning.PositioningCardSubmissionDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.account.MediaAccountMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.lead.PersonMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.positioning.PositioningCardMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.positioning.PositioningCardSubmissionMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.registration.ServiceRelationMapper;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.registration.ServiceRelationDO;
import cn.iocoder.yudao.module.zsjos.enums.MediaWorkflowConstants;
import cn.iocoder.yudao.module.zsjos.service.director.DirectorFormTemplateService;
import cn.iocoder.yudao.module.zsjos.controller.admin.director.vo.DirectorFormTemplateVO;
import cn.iocoder.yudao.module.zsjos.service.media.MediaWorkflowEventService;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.personnel.PartnerAccountDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.personnel.PartnerStudentLinkDO;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.junit.jupiter.api.extension.ExtendWith;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PositioningCardServiceTest {
    @Mock private PositioningCardMapper mapper;
    @Mock private PositioningCardSubmissionMapper submissionMapper;
    @Mock private BpmProcessInstanceApi processInstanceApi;
    @Mock private MediaAccountMapper accountMapper;
    @Mock private PersonMapper personMapper;
    @Mock private ServiceRelationMapper relationMapper;
    @Mock private DirectorFormTemplateService directorFormTemplateService;
    @Mock private cn.iocoder.yudao.module.system.api.permission.PermissionApi permissionApi;
    @Mock private PostApi postApi;
    @Mock private AdminUserApi adminUserApi;
    @Mock private PositioningCardObjectPermissionProvider objectPermissionProvider;
    @Mock private cn.iocoder.yudao.module.zsjos.service.common.MediaDataScopeService dataScopeService;
    @Mock private cn.iocoder.yudao.module.zsjos.dal.mysql.personnel.PartnerStudentLinkMapper partnerStudentLinkMapper;
    @Mock private cn.iocoder.yudao.module.zsjos.dal.mysql.personnel.PartnerAccountMapper partnerAccountMapper;
    @Mock private MediaWorkflowEventService workflowEventService;
    @InjectMocks private PositioningCardService service;

    @Test
    void ordinarySubmitGoesToOperatorWithoutStartingBpm() {
        PositioningCardDO card = card(false, MediaWorkflowConstants.POSITIONING_CO_CREATING, 0)
                .setServiceRelationId(30L);
        when(mapper.selectById(1L)).thenReturn(card);
        mockAssignedOperator();
        when(mapper.transitionWithOperator(1L, 0, MediaWorkflowConstants.POSITIONING_CO_CREATING,
                MediaWorkflowConstants.POSITIONING_OPERATOR_FEASIBILITY, 88L)).thenReturn(1);

        service.submitReview(1L, 0, 99L);

        verifyNoInteractions(processInstanceApi);
        verify(mapper).transitionWithOperator(1L, 0, MediaWorkflowConstants.POSITIONING_CO_CREATING,
                MediaWorkflowConstants.POSITIONING_OPERATOR_FEASIBILITY, 88L);
        verify(submissionMapper).insert(argThat((PositioningCardSubmissionDO value) -> value.getSubmissionNo() == 1
                && Long.valueOf(88L).equals(value.getOperatorUserId())
                && MediaWorkflowConstants.POSITIONING_OPERATOR_FEASIBILITY.equals(value.getStatus())));
    }

    @Test
    void createRequiresMatchingStudentAndDefaultsUnfilledJsonLayers() {
        when(accountMapper.selectById(10L)).thenReturn(new cn.iocoder.yudao.module.zsjos.dal.dataobject.account.MediaAccountDO()
                .setId(10L).setStudentPersonId(20L).setDirectorUserId(99L));
        when(personMapper.selectById(20L)).thenReturn(new cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.PersonDO().setId(20L));
        ServiceRelationDO relation = new ServiceRelationDO().setId(30L).setPersonId(20L).setContentDirectorUserId(99L).setStatus("active")
                .setAcceptanceStatus("accepted").setDirectorStage("positioning_ready");
        when(relationMapper.selectActiveByPersonIds(java.util.List.of(20L))).thenReturn(java.util.List.of(relation));
        when(relationMapper.selectByIdForUpdate(30L, 1L)).thenReturn(relation);
        DirectorFormTemplateVO.Snapshot snapshot = new DirectorFormTemplateVO.Snapshot();
        snapshot.setTemplateId(40L); snapshot.setTemplateVersionId(41L); snapshot.setTemplateVersionNo(1);
        snapshot.setFields(java.util.List.of()); snapshot.setValues(java.util.Map.of());
        snapshot.setDictSnapshots(java.util.Map.of());
        when(directorFormTemplateService.validateAndSnapshot(DirectorFormTemplateService.SCENE_POSITIONING,
                null, null, false)).thenReturn(snapshot);
        doAnswer(invocation -> { invocation.<PositioningCardDO>getArgument(0).setId(7L); return 1; })
                .when(mapper).insert(any(PositioningCardDO.class));
        PositioningCardSaveReqVO req = new PositioningCardSaveReqVO();
        req.setAccountId(10L);
        req.setStudentPersonId(20L);
        req.setLayer1Json("{\"persona\":\"test\"}");

        cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder.setTenantId(1L);
        PositioningCardDraftRespVO result;
        try {
            result = service.create(req, 99L);
        } finally {
            cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder.clear();
        }

        assertEquals(7L, result.getId());
        assertEquals(0, result.getVersion());
        verify(personMapper).selectById(20L);
        verify(mapper).insert(argThat((PositioningCardDO card) -> "{}".equals(card.getLayer2Json())
                && "{}".equals(card.getFormulaJson()) && "{}".equals(card.getComplianceJson())
                && card.getTrialEndDate() == null));
    }

    @Test
    void createRejectsDifferentContentWhenARecentDraftAlreadyExists() {
        var account = new cn.iocoder.yudao.module.zsjos.dal.dataobject.account.MediaAccountDO()
                .setId(10L).setStudentPersonId(20L).setDirectorUserId(99L);
        ServiceRelationDO relation = new ServiceRelationDO().setId(30L).setPersonId(20L).setContentDirectorUserId(99L).setStatus("active")
                .setAcceptanceStatus("accepted").setDirectorStage("positioning_ready");
        when(accountMapper.selectById(10L)).thenReturn(account);
        when(personMapper.selectById(20L)).thenReturn(new cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.PersonDO().setId(20L));
        when(relationMapper.selectActiveByPersonIds(java.util.List.of(20L))).thenReturn(java.util.List.of(relation));
        when(relationMapper.selectByIdForUpdate(30L, 1L)).thenReturn(relation);
        DirectorFormTemplateVO.Snapshot snapshot = new DirectorFormTemplateVO.Snapshot();
        snapshot.setTemplateId(40L); snapshot.setTemplateVersionId(41L); snapshot.setTemplateVersionNo(1);
        snapshot.setFields(java.util.List.of()); snapshot.setValues(java.util.Map.of("new", "value"));
        snapshot.setDictSnapshots(java.util.Map.of());
        when(directorFormTemplateService.validateAndSnapshot(DirectorFormTemplateService.SCENE_POSITIONING,
                40L, java.util.Map.of("new", "value"), false)).thenReturn(snapshot);
        PositioningCardDO existing = card(false, MediaWorkflowConstants.POSITIONING_CO_CREATING, 2)
                .setServiceRelationId(30L).setTemplateVersionId(41L).setValuesSnapshotJson("{\"old\":\"value\"}")
                .setLayer1Json("{}").setLayer2Json("{}").setFormulaJson("{}").setFeasibilityJson("{}")
                .setContentFormJson("{}").setComplianceJson("{}");
        when(mapper.selectLatestCreatingDraft(30L, 10L, 1L)).thenReturn(existing);
        PositioningCardSaveReqVO req = new PositioningCardSaveReqVO();
        req.setAccountId(10L); req.setStudentPersonId(20L); req.setServiceRelationId(30L);
        req.setTemplateId(40L); req.setValues(java.util.Map.of("new", "value"));

        cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder.setTenantId(1L);
        try {
            assertThrows(cn.iocoder.yudao.framework.common.exception.ServiceException.class,
                    () -> service.create(req, 99L));
        } finally {
            cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder.clear();
        }
        verify(mapper, never()).insert(any(PositioningCardDO.class));
    }

    @Test
    void createReplaysTheLatestDraftWhenContentMatches() {
        var account = new cn.iocoder.yudao.module.zsjos.dal.dataobject.account.MediaAccountDO()
                .setId(10L).setStudentPersonId(20L).setDirectorUserId(99L);
        ServiceRelationDO relation = new ServiceRelationDO().setId(30L).setPersonId(20L)
                .setContentDirectorUserId(99L).setStatus("active")
                .setAcceptanceStatus("accepted").setDirectorStage("positioning_ready");
        when(accountMapper.selectById(10L)).thenReturn(account);
        when(personMapper.selectById(20L)).thenReturn(new cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.PersonDO().setId(20L));
        when(relationMapper.selectActiveByPersonIds(java.util.List.of(20L))).thenReturn(java.util.List.of(relation));
        when(relationMapper.selectByIdForUpdate(30L, 1L)).thenReturn(relation);
        DirectorFormTemplateVO.Snapshot snapshot = new DirectorFormTemplateVO.Snapshot();
        snapshot.setTemplateId(40L); snapshot.setTemplateVersionId(41L); snapshot.setTemplateVersionNo(1);
        snapshot.setFields(java.util.List.of()); snapshot.setValues(java.util.Map.of()); snapshot.setDictSnapshots(java.util.Map.of());
        when(directorFormTemplateService.validateAndSnapshot(DirectorFormTemplateService.SCENE_POSITIONING,
                40L, java.util.Map.of(), false)).thenReturn(snapshot);
        java.time.LocalDate trialEndDate = java.time.LocalDate.now().plusDays(14);
        PositioningCardDO existing = card(false, MediaWorkflowConstants.POSITIONING_CO_CREATING, 3)
                .setId(8L).setServiceRelationId(30L).setTemplateVersionId(41L).setValuesSnapshotJson("{}")
                .setTrialEndDate(trialEndDate).setLayer1Json("{}").setLayer2Json("{}").setFormulaJson("{}")
                .setFeasibilityJson("{}").setContentFormJson("{}").setComplianceJson("{}");
        when(mapper.selectLatestCreatingDraft(30L, 10L, 1L)).thenReturn(existing);
        PositioningCardSaveReqVO req = new PositioningCardSaveReqVO();
        req.setAccountId(10L); req.setStudentPersonId(20L); req.setServiceRelationId(30L);
        req.setTemplateId(40L); req.setValues(java.util.Map.of()); req.setTrialEndDate(trialEndDate);

        cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder.setTenantId(1L);
        PositioningCardDraftRespVO result;
        try {
            result = service.create(req, 99L);
        } finally {
            cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder.clear();
        }

        assertEquals(8L, result.getId());
        assertEquals(3, result.getVersion());
        verify(mapper, never()).insert(any(PositioningCardDO.class));
    }

    @Test
    void updateDraftPersistsAllEditablePayloadsAndAuthoritativeVersion() {
        PositioningCardDO existing = editableDraft(2);
        when(mapper.selectById(1L)).thenReturn(existing);
        DirectorFormTemplateVO.Snapshot snapshot = positioningSnapshot(java.util.Map.of("contentPillars", java.util.List.of("one")));
        when(directorFormTemplateService.validateAndSnapshotVersion(eq(DirectorFormTemplateService.SCENE_POSITIONING),
                eq(41L), any(), eq(false), any())).thenReturn(snapshot);
        when(mapper.updateDraftSnapshot(any(), eq(2), eq(MediaWorkflowConstants.POSITIONING_CO_CREATING))).thenReturn(1);
        PositioningCardSaveReqVO req = new PositioningCardSaveReqVO();
        req.setAccountId(10L); req.setVersion(2); req.setValues(snapshot.getValues());
        req.setLayer1Json("{\"persona\":\"updated\"}"); req.setProfessionalRisk(true);

        PositioningCardDraftRespVO result = service.updateDraft(1L, req, 99L);

        assertEquals(3, result.getVersion());
        verify(mapper).updateDraftSnapshot(argThat((PositioningCardDO card) -> card.getVersion() == 3
                && "{\"persona\":\"updated\"}".equals(card.getLayer1Json())
                && "{}".equals(card.getLayer2Json()) && Boolean.TRUE.equals(card.getProfessionalRisk())),
                eq(2), eq(MediaWorkflowConstants.POSITIONING_CO_CREATING));
    }

    @Test
    void updateDraftReplaysAnIdenticalCommittedRequest() {
        PositioningCardDO existing = editableDraft(3).setValuesSnapshotJson("{\"contentPillars\":[\"one\"]}");
        when(mapper.selectById(1L)).thenReturn(existing);
        DirectorFormTemplateVO.Snapshot snapshot = positioningSnapshot(java.util.Map.of("contentPillars", java.util.List.of("one")));
        when(directorFormTemplateService.validateAndSnapshotVersion(eq(DirectorFormTemplateService.SCENE_POSITIONING),
                eq(41L), any(), eq(false), any())).thenReturn(snapshot);
        PositioningCardSaveReqVO req = new PositioningCardSaveReqVO();
        req.setAccountId(10L); req.setVersion(2); req.setValues(snapshot.getValues());

        PositioningCardDraftRespVO result = service.updateDraft(1L, req, 99L);

        assertEquals(3, result.getVersion());
        verify(mapper, never()).updateDraftSnapshot(any(), any(), any());
    }

    @Test
    void professionalSubmitStartsBpmOnceAndEntersIpReview() {
        PositioningCardDO card = card(true, MediaWorkflowConstants.POSITIONING_CO_CREATING, 0)
                .setServiceRelationId(30L);
        when(mapper.selectById(1L)).thenReturn(card);
        mockAssignedOperator();
        PostRespDTO post = new PostRespDTO();
        post.setId(30L); post.setStatus(0);
        AdminUserRespDTO reviewer = new AdminUserRespDTO();
        reviewer.setId(254L); reviewer.setStatus(0);
        AdminUserRespDTO disabled = new AdminUserRespDTO();
        disabled.setId(255L); disabled.setStatus(1);
        when(postApi.getPostByCode(MediaWorkflowConstants.POST_CODE_IP_TEACHER)).thenReturn(post);
        when(adminUserApi.getUserListByPostIds(java.util.List.of(30L)))
                .thenReturn(java.util.List.of(reviewer, disabled));
        when(processInstanceApi.createProcessInstance(eq(99L), any())).thenReturn("process-1");
        when(mapper.updateByVersion(any(), eq(0), eq(MediaWorkflowConstants.POSITIONING_CO_CREATING))).thenReturn(1);

        service.submitReview(1L, 0, 99L);

        verify(processInstanceApi, times(1)).createProcessInstance(eq(99L), argThat((BpmProcessInstanceCreateReqDTO request) ->
                java.util.List.of(254L).equals(request.getStartUserSelectAssignees().get("ipReviewer"))
                        && Long.valueOf(254L).equals(request.getVariables().get("assignee"))
                        && java.util.List.of(254L).equals(request.getVariables().get("coll_userList"))));
        verify(mapper).updateByVersion(argThat(value ->
                MediaWorkflowConstants.POSITIONING_IP_REVIEW.equals(value.getStatus())
                        && "process-1".equals(value.getIpProcessInstanceId())
                        && Long.valueOf(254L).equals(value.getIpReviewerUserId())), eq(0),
                eq(MediaWorkflowConstants.POSITIONING_CO_CREATING));
    }

    @Test
    void ipAndOperatorRejectionsReturnToCoCreating() {
        PositioningCardDO ip = card(true, MediaWorkflowConstants.POSITIONING_IP_REVIEW, 1);
        when(mapper.selectByIpProcessId("process-1")).thenReturn(ip);
        when(mapper.transition(1L, 1, MediaWorkflowConstants.POSITIONING_IP_REVIEW,
                MediaWorkflowConstants.POSITIONING_CO_CREATING)).thenReturn(1);
        PositioningCardSubmissionDO ipSubmission = submission(11L, MediaWorkflowConstants.POSITIONING_IP_REVIEW, 0);
        when(submissionMapper.selectLatestByCard(1L)).thenReturn(ipSubmission);
        when(submissionMapper.markStatus(11L, 0, MediaWorkflowConstants.POSITIONING_IP_REVIEW,
                "ip_rejected")).thenReturn(1);
        service.handleIpProcessResult("process-1", 3, "rejected");
        verify(mapper).transition(1L, 1, MediaWorkflowConstants.POSITIONING_IP_REVIEW,
                MediaWorkflowConstants.POSITIONING_CO_CREATING);

        PositioningCardDO operator = card(false, MediaWorkflowConstants.POSITIONING_OPERATOR_FEASIBILITY, 2);
        operator.setId(2L).setOperatorUserId(88L);
        when(mapper.selectById(2L)).thenReturn(operator);
        PositioningCardSubmissionDO operatorSubmission = submission(12L,
                MediaWorkflowConstants.POSITIONING_OPERATOR_FEASIBILITY, 0).setCardId(2L);
        when(submissionMapper.selectLatestByCard(2L)).thenReturn(operatorSubmission);
        when(submissionMapper.markOperatorDecision(eq(12L), eq(0),
                eq(MediaWorkflowConstants.POSITIONING_OPERATOR_FEASIBILITY), eq("operator_rejected"),
                eq(88L), any(), eq("未填写退回原因"))).thenReturn(1);
        when(mapper.transitionOperatorReview(eq(2L), eq(2),
                eq(MediaWorkflowConstants.POSITIONING_OPERATOR_FEASIBILITY),
                eq(MediaWorkflowConstants.POSITIONING_CO_CREATING), eq(88L), any(),
                eq("未填写退回原因"))).thenReturn(1);
        try (var security = mockStatic(cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils.class)) {
            security.when(cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils::getLoginUserId)
                    .thenReturn(88L);
            service.operatorReject(2L, 2);
        }
        verify(mapper).transitionOperatorReview(eq(2L), eq(2),
                eq(MediaWorkflowConstants.POSITIONING_OPERATOR_FEASIBILITY),
                eq(MediaWorkflowConstants.POSITIONING_CO_CREATING), eq(88L), any(),
                eq("未填写退回原因"));
    }

    @Test
    void operatorApprovalAdvancesLatestSubmissionToStudentLinkPending() {
        PositioningCardDO card = card(false, MediaWorkflowConstants.POSITIONING_OPERATOR_FEASIBILITY, 2);
        card.setCardNo("PC-202608210001").setOperatorUserId(88L);
        when(mapper.selectById(1L)).thenReturn(card);
        PositioningCardSubmissionDO submission = submission(13L,
                MediaWorkflowConstants.POSITIONING_OPERATOR_FEASIBILITY, 0);
        when(submissionMapper.selectLatestByCard(1L)).thenReturn(submission);
        when(submissionMapper.markOperatorDecision(eq(13L), eq(0),
                eq(MediaWorkflowConstants.POSITIONING_OPERATOR_FEASIBILITY),
                eq(MediaWorkflowConstants.POSITIONING_STUDENT_LINK_PENDING), eq(88L), any(), isNull())).thenReturn(1);
        when(mapper.transitionOperatorReview(eq(1L), eq(2),
                eq(MediaWorkflowConstants.POSITIONING_OPERATOR_FEASIBILITY),
                eq(MediaWorkflowConstants.POSITIONING_STUDENT_LINK_PENDING), eq(88L), any(), isNull())).thenReturn(1);

        try (var security = mockStatic(cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils.class)) {
            security.when(cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils::getLoginUserId)
                    .thenReturn(88L);
            service.operatorApprove(1L, 2);
        }

        verify(mapper).transitionOperatorReview(eq(1L), eq(2),
                eq(MediaWorkflowConstants.POSITIONING_OPERATOR_FEASIBILITY),
                eq(MediaWorkflowConstants.POSITIONING_STUDENT_LINK_PENDING), eq(88L), any(), isNull());
        verifyNoInteractions(partnerStudentLinkMapper, partnerAccountMapper);
    }

    @Test
    void submitWithoutAssignedOperatorDoesNotMutateDraftOrCreateSubmission() {
        PositioningCardDO card = card(false, MediaWorkflowConstants.POSITIONING_CO_CREATING, 4)
                .setServiceRelationId(30L);
        when(mapper.selectById(1L)).thenReturn(card);
        when(relationMapper.selectById(30L)).thenReturn(new ServiceRelationDO().setId(30L)
                .setStatus("active").setAcceptanceStatus("accepted"));

        assertThrows(cn.iocoder.yudao.framework.common.exception.ServiceException.class,
                () -> service.submitReview(1L, 4, 99L));

        verify(submissionMapper, never()).insert(any(PositioningCardSubmissionDO.class));
        verify(mapper, never()).transitionWithOperator(any(), any(), any(), any(), any());
        assertEquals(MediaWorkflowConstants.POSITIONING_CO_CREATING, card.getStatus());
        assertEquals(4, card.getVersion());
    }

    private PositioningCardDO card(boolean professionalRisk, String status, int version) {
        return new PositioningCardDO().setId(1L).setAccountId(10L).setStudentPersonId(20L)
                .setProfessionalRisk(professionalRisk).setStatus(status).setVersion(version).setVersionNo(1);
    }

    private PositioningCardDO editableDraft(int version) {
        return card(false, MediaWorkflowConstants.POSITIONING_CO_CREATING, version)
                .setDirectorUserId(99L).setTemplateVersionId(41L).setValuesSnapshotJson("{}")
                .setLayer1Json("{}").setLayer2Json("{}").setFormulaJson("{}").setFeasibilityJson("{}")
                .setContentFormJson("{}").setComplianceJson("{}");
    }

    private void mockAssignedOperator() {
        ServiceRelationDO relation = new ServiceRelationDO().setId(30L).setStatus("active")
                .setAcceptanceStatus("accepted").setOperatorUserId(88L);
        when(relationMapper.selectById(30L)).thenReturn(relation);
        AdminUserRespDTO operator = new AdminUserRespDTO();
        operator.setId(88L); operator.setStatus(0);
        when(adminUserApi.getUser(88L)).thenReturn(operator);
    }

    private PositioningCardSubmissionDO submission(Long id, String status, Integer version) {
        return new PositioningCardSubmissionDO().setId(id).setCardId(1L).setOperatorUserId(88L)
                .setStatus(status).setVersion(version).setSubmissionNo(1);
    }

    private DirectorFormTemplateVO.Snapshot positioningSnapshot(java.util.Map<String, Object> values) {
        DirectorFormTemplateVO.Snapshot snapshot = new DirectorFormTemplateVO.Snapshot();
        snapshot.setTemplateId(40L); snapshot.setTemplateVersionId(41L); snapshot.setTemplateVersionNo(1);
        snapshot.setFields(java.util.List.of()); snapshot.setValues(values); snapshot.setDictSnapshots(java.util.Map.of());
        return snapshot;
    }
}
