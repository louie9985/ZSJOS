package cn.iocoder.yudao.module.zsjos.service.positioning;

import cn.iocoder.yudao.module.bpm.api.task.BpmProcessInstanceApi;
import cn.iocoder.yudao.module.bpm.api.task.dto.BpmProcessInstanceCreateReqDTO;
import cn.iocoder.yudao.module.system.api.dept.PostApi;
import cn.iocoder.yudao.module.system.api.dept.dto.PostRespDTO;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import cn.iocoder.yudao.module.system.api.user.dto.AdminUserRespDTO;
import cn.iocoder.yudao.module.zsjos.controller.admin.positioning.vo.PositioningCardSaveReqVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.positioning.vo.PositioningCardDraftRespVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.positioning.vo.PositioningCardImportReqVO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.account.MediaAccountDO;
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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PositioningCardServiceTest {
    @org.mockito.Mock private cn.iocoder.yudao.module.zsjos.service.media.MediaCollaborationNotifyPublisher collaborationNotify;
    @Test void attachmentWrongDirectorReportsPermissionInsteadOfVersionConflict() {
        when(mapper.selectById(1L)).thenReturn(editableDraft(2));
        var error = assertThrows(cn.iocoder.yudao.framework.common.exception.ServiceException.class,
                () -> service.uploadAttachment(1L, "attachment", new byte[] {1}, "test.txt", "text/plain", 88L));
        assertEquals(cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants.POSITIONING_CARD_PERMISSION_DENIED.getCode(), error.getCode());
        verify(mapper, never()).updateDraftSnapshot(any(), any(), any());
    }

    @Test void accountOverviewUsesFrozenSubmissionRatherThanAccountProfileValues() {
        when(accountMapper.selectById(10L)).thenReturn(scopedAccount(10L));
        var row = new PositioningCardSubmissionDO().setId(800L).setCardId(70L).setAccountId(10L)
                .setStatus("confirmed").setSubmissionNo(2).setFieldsSnapshotJson("[]")
                .setValuesSnapshotJson("{\"pc_account_name\":\"frozen-name\"}")
                .setDictSnapshotJson("{}");
        when(submissionMapper.selectCurrentConfirmedByAccount(10L)).thenReturn(row);
        var result = service.accountOverview(10L, 99L);
        assertEquals("frozen-name", result.effective().getValuesSnapshot().get("pc_account_name"));
        assertEquals(70L, result.effective().getId());
        assertTrue(result.history().isEmpty());
        verify(objectPermissionProvider, atLeastOnce()).check(70L, "read", 99L);
    }

    @Test void accountOverviewFailsWhenCardReadIsDenied() {
        when(accountMapper.selectById(10L)).thenReturn(scopedAccount(10L));
        when(submissionMapper.selectCurrentConfirmedByAccount(10L)).thenReturn(new PositioningCardSubmissionDO().setCardId(70L));
        doThrow(new IllegalStateException("denied")).when(objectPermissionProvider).check(70L, "read", 99L);
        assertThrows(IllegalStateException.class, () -> service.accountOverview(10L, 99L));
    }

    @Test void updatingDraftUpgradesTemplateAndRetainsRetiredValues() {
        var existing = editableDraft(2).setValuesSnapshotJson("{\"oldField\":\"retained\"}");
        when(mapper.selectById(1L)).thenReturn(existing);
        when(directorFormTemplateService.requirePublished(eq(DirectorFormTemplateService.SCENE_POSITIONING), any()))
                .thenReturn(new cn.iocoder.yudao.module.zsjos.dal.dataobject.director.DirectorFormTemplateVersionDO().setId(42L));
        var snapshot = positioningSnapshot(java.util.Map.of("pc_account_name", "new name"));
        when(directorFormTemplateService.validateAndSnapshotVersion(eq(DirectorFormTemplateService.SCENE_POSITIONING), eq(42L), any(), eq(false), any())).thenReturn(snapshot);
        when(mapper.updateDraftSnapshot(any(), eq(2), eq(MediaWorkflowConstants.POSITIONING_CO_CREATING))).thenReturn(1);
        var req = new PositioningCardSaveReqVO(); req.setAccountId(10L); req.setVersion(2); req.setValues(snapshot.getValues());
        service.updateDraft(1L, req, 99L);
        verify(mapper).updateDraftSnapshot(argThat(card -> card.getTemplateVersionId().equals(42L)
                && card.getValuesSnapshotJson().contains("retained") && card.getValuesSnapshotJson().contains("new name")), eq(2), any());
        verify(submissionMapper, never()).insert(any(PositioningCardSubmissionDO.class));
    }
    @Mock private PositioningAssignmentService assignmentService;
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
    @org.junit.jupiter.api.AfterEach void clearTenant(){cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder.clear();}
    private MediaAccountDO scopedAccount(Long id){var a=new MediaAccountDO().setId(id).setStudentPersonId(20L).setCreateServiceRelationId(30L).setDirectorUserId(99L);a.setTenantId(1L);return a;}

    @Test
    void missingRelationCannotBeInferredFromStudentOrDirector() {
        var req=new PositioningCardSaveReqVO();req.setAccountId(10L);req.setStudentPersonId(20L);
        assertThrows(cn.iocoder.yudao.framework.common.exception.ServiceException.class,()->service.create(req,99L));
        verifyNoInteractions(accountMapper,relationMapper,mapper,directorFormTemplateService);
    }

    @Test
    void importTargetRejectsAccountAndRelationFromAnotherTenant() {
        cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder.setTenantId(1L);
        when(personMapper.selectById(20L)).thenReturn(new cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.PersonDO().setId(20L));
        var relation=importRelation();relation.setTenantId(2L);
        when(relationMapper.selectByIdForUpdate(30L,1L)).thenReturn(relation);
        assertThrows(cn.iocoder.yudao.framework.common.exception.ServiceException.class,()->service.importSubmission(importRequest(),99L));
        verifyNoInteractions(submissionMapper,directorFormTemplateService);
    }

    @Test
    void forgedCrossRelationSourceCombinationsNeverReachTemplateOrWrite() {
        mockImportTarget();
        var source=importSubmissionSource();
        var sourceCard=card(false,"operator_feasibility",1).setId(501L).setAccountId(11L);
        when(submissionMapper.selectById(101L)).thenReturn(source);
        when(mapper.selectById(501L)).thenReturn(sourceCard);
        cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder.setTenantId(1L);
        try {
            source.setServiceRelationId(31L);
            assertThrows(cn.iocoder.yudao.framework.common.exception.ServiceException.class,()->service.importSubmission(importRequest(),99L));
            source.setServiceRelationId(30L);sourceCard.setServiceRelationId(31L);
            assertThrows(cn.iocoder.yudao.framework.common.exception.ServiceException.class,()->service.importSubmission(importRequest(),99L));
            sourceCard.setServiceRelationId(30L);sourceCard.setStudentPersonId(21L);
            assertThrows(cn.iocoder.yudao.framework.common.exception.ServiceException.class,()->service.importSubmission(importRequest(),99L));
        } finally {cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder.clear();}
        verifyNoInteractions(directorFormTemplateService,objectPermissionProvider);
        verify(mapper,never()).insert(any(PositioningCardDO.class));
    }

    @Test
    void queryAllReadPermissionDoesNotAuthorizeImportTarget() {
        when(personMapper.selectById(20L)).thenReturn(new cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.PersonDO().setId(20L));
        when(relationMapper.selectByIdForUpdate(30L,1L)).thenReturn(importRelation().setContentDirectorUserId(55L));
        lenient().when(permissionApi.hasAnyPermissions(99L,"zsjos:positioning-card:query-all")).thenReturn(true);
        cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder.setTenantId(1L);
        try { assertThrows(cn.iocoder.yudao.framework.common.exception.ServiceException.class,()->service.importSubmission(importRequest(),99L)); }
        finally {cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder.clear();}
        verifyNoInteractions(submissionMapper,directorFormTemplateService);
    }

    @Test
    void unboundCardSubmitsToOperatorWithoutAccountOrBpm() {
        PositioningCardDO card = card(false, MediaWorkflowConstants.POSITIONING_CO_CREATING, 0)
                .setServiceRelationId(30L).setAccountId(null);
        when(mapper.selectById(1L)).thenReturn(card);
        mockAssignedOperator();
        when(mapper.transitionWithOperator(1L, 0, MediaWorkflowConstants.POSITIONING_CO_CREATING,
                MediaWorkflowConstants.POSITIONING_OPERATOR_FEASIBILITY, 88L)).thenReturn(1);

        service.submitReview(1L, 0, 99L);

        verifyNoInteractions(accountMapper);
        verify(workflowEventService).notify(eq("media.positioning.operator_review"), eq(MediaWorkflowConstants.BIZ_TYPE_POSITIONING_CARD), eq(1L), eq(88L), eq(99L), anyString(), anyMap());
        verifyNoInteractions(processInstanceApi);
        verify(mapper).transitionWithOperator(1L, 0, MediaWorkflowConstants.POSITIONING_CO_CREATING,
                MediaWorkflowConstants.POSITIONING_OPERATOR_FEASIBILITY, 88L);
        verify(submissionMapper).insert(argThat((PositioningCardSubmissionDO value) -> value.getSubmissionNo() == 1
                && Long.valueOf(88L).equals(value.getOperatorUserId())
                && MediaWorkflowConstants.POSITIONING_OPERATOR_FEASIBILITY.equals(value.getStatus())));
    }

    @Test
    void retrySubmitDoesNotCreateAnotherRound() {
        when(mapper.selectById(1L)).thenReturn(card(false, MediaWorkflowConstants.POSITIONING_OPERATOR_FEASIBILITY, 1));
        service.submitReview(1L, 0, 99L);
        verifyNoInteractions(submissionMapper, workflowEventService, relationMapper);
    }

    @Test
    void createRequiresMatchingStudentAndDefaultsUnfilledJsonLayers() {
        when(personMapper.selectById(20L)).thenReturn(new cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.PersonDO().setId(20L));
        ServiceRelationDO relation = new ServiceRelationDO().setId(30L).setPersonId(20L).setContentDirectorUserId(99L).setStatus("active")
                .setAcceptanceStatus("accepted").setDirectorStage("positioning_interview_completed");
        relation.setTenantId(1L);
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
        req.setServiceRelationId(30L);
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
    void createAllowsStudentDraftBeforeAccountExists() {
        cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder.setTenantId(1L);
        try {
            ServiceRelationDO relation = new ServiceRelationDO().setId(30L).setPersonId(20L)
                    .setContentDirectorUserId(99L).setStatus("active").setAcceptanceStatus("accepted")
                    .setDirectorStage("positioning_interview_completed");
            relation.setTenantId(1L);
            when(personMapper.selectById(20L)).thenReturn(new cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.PersonDO().setId(20L));
            when(relationMapper.selectByIdForUpdate(30L, 1L)).thenReturn(relation);
            var snapshot = new DirectorFormTemplateVO.Snapshot();
            snapshot.setTemplateId(40L); snapshot.setTemplateVersionId(41L);
            snapshot.setFields(java.util.List.of()); snapshot.setValues(java.util.Map.of());
            snapshot.setDictSnapshots(java.util.Map.of());
            when(directorFormTemplateService.validateAndSnapshot(DirectorFormTemplateService.SCENE_POSITIONING,
                    null, null, false)).thenReturn(snapshot);
            doAnswer(invocation -> { invocation.<PositioningCardDO>getArgument(0).setId(8L); return 1; })
                    .when(mapper).insert(any(PositioningCardDO.class));
            var req = new PositioningCardSaveReqVO();
            req.setStudentPersonId(20L); req.setServiceRelationId(30L);
            assertEquals(8L, service.create(req, 99L).getId());
            verify(accountMapper, never()).selectById(any());
            verify(mapper).insert(argThat((PositioningCardDO card) -> card.getAccountId() == null
                    && Long.valueOf(20L).equals(card.getStudentPersonId())
                    && Long.valueOf(30L).equals(card.getServiceRelationId())));
        } finally {
            cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder.clear();
        }
    }

    @Test
    void importSourcesIncludesReadableSubmissionsFromCurrentAndOtherAccounts() {
        cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder.setTenantId(1L);
        MediaAccountDO current = new MediaAccountDO().setId(10L).setStudentPersonId(20L)
                .setCreateServiceRelationId(30L)
                .setDirectorUserId(99L).setNickname("当前账号");
        MediaAccountDO other = new MediaAccountDO().setId(11L).setStudentPersonId(20L)
                .setCreateServiceRelationId(30L)
                .setDirectorUserId(99L).setNickname("其他账号");
        ServiceRelationDO relation = importRelation();
        current.setTenantId(1L);other.setTenantId(1L);
        when(personMapper.selectById(20L)).thenReturn(
                new cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.PersonDO().setId(20L));
        when(relationMapper.selectByIdForUpdate(30L, 1L)).thenReturn(relation);
        PositioningCardSubmissionDO first = submission(101L, "operator_feasibility", 0)
                .setCardId(501L).setAccountId(10L).setStudentPersonId(20L).setSubmissionNo(1);
        PositioningCardSubmissionDO second = submission(102L, "student_confirm", 0)
                .setCardId(502L).setAccountId(11L).setStudentPersonId(20L).setSubmissionNo(2);
        when(submissionMapper.selectByService(30L)).thenReturn(java.util.List.of(first, second));
        when(mapper.selectById(501L)).thenReturn(card(false, "operator_feasibility", 1)
                .setId(501L).setAccountId(10L).setStudentPersonId(20L).setCardNo("PC-1"));
        when(mapper.selectById(502L)).thenReturn(card(false, "student_confirm", 2)
                .setId(502L).setAccountId(11L).setStudentPersonId(20L).setCardNo("PC-2"));
        when(objectPermissionProvider.hasPermission(any(), eq("read"), eq(99L))).thenReturn(true);

        var result = service.getImportSources(20L, 10L, 30L, 99L);

        assertEquals(2, result.size());
        assertFalse(result.get(0).getSameAccount());
        assertEquals("PC-2", result.get(1).getAccountLabel());
    }

    @Test
    void importSubmissionMapsCompatibleFieldsAndPreservesDictionarySnapshot() {
        mockImportTarget();
        MediaAccountDO sourceAccount = new MediaAccountDO().setId(11L).setStudentPersonId(20L).setCreateServiceRelationId(30L);
        PositioningCardSubmissionDO source = importSubmissionSource();
        when(submissionMapper.selectById(101L)).thenReturn(source);
        when(mapper.selectById(501L)).thenReturn(card(false, "operator_feasibility", 1)
                .setId(501L).setAccountId(11L).setStudentPersonId(20L));
        DirectorFormTemplateVO.Field name = field("name", "text", null);
        DirectorFormTemplateVO.Field category = field("category", "radio", "positioning_category");
        DirectorFormTemplateVO.Field changed = field("changed", "number", null);
        DirectorFormTemplateVO.Field added = field("added", "text", null);
        DirectorFormTemplateVO.Snapshot published = positioningSnapshot(java.util.Map.of());
        published.setFields(java.util.List.of(name, category, changed, added));
        when(directorFormTemplateService.validateAndSnapshot(DirectorFormTemplateService.SCENE_POSITIONING,
                null, java.util.Map.of(), false)).thenReturn(published);
        var mappedValues = java.util.Map.<String, Object>of("name", "复用姓名", "category", "expert");
        var dictSnapshot = java.util.Map.<String, Object>of("category",
                java.util.Map.of("value", "expert", "labelSnapshot", "专家型", "dictType", "positioning_category"));
        DirectorFormTemplateVO.Snapshot mapped = positioningSnapshot(mappedValues);
        mapped.setFields(published.getFields()); mapped.setDictSnapshots(dictSnapshot);
        when(directorFormTemplateService.validateAndSnapshotVersion(DirectorFormTemplateService.SCENE_POSITIONING,
                41L, mappedValues, false, dictSnapshot)).thenReturn(mapped);
        when(assignmentService.masterId(30L)).thenReturn(700L);
        when(mapper.selectById(700L)).thenReturn(editableDraft(0).setId(700L));
        when(mapper.overwriteDraftFromImport(any(), eq(0))).thenReturn(1);

        PositioningCardImportReqVO request = importRequest(); request.setTargetDraftId(700L); request.setVersion(0);
        cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder.setTenantId(1L);
        try {
            var result = service.importSubmission(request, 99L);
            assertEquals(700L, result.getId());
            assertEquals(mappedValues, result.getValues());
            assertTrue(result.getSkippedFieldKeys().containsAll(java.util.List.of("removed", "changed")));
        } finally {
            cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder.clear();
        }
        verify(submissionMapper, never()).insert(any(PositioningCardSubmissionDO.class));
        verify(mapper).overwriteDraftFromImport(argThat((PositioningCardDO draft) -> draft.getId().equals(700L)
                && draft.getTemplateVersionId().equals(41L)
                && draft.getValuesSnapshotJson().contains("复用姓名")
                && draft.getDictSnapshotJson().contains("专家型")), eq(0));
    }

    @Test
    void importSubmissionOverwritesOnlyExpectedDraftVersion() {
        mockImportTarget();
        PositioningCardSubmissionDO source = importSubmissionSource();
        when(submissionMapper.selectById(101L)).thenReturn(source);
        when(mapper.selectById(501L)).thenReturn(card(false, "operator_feasibility", 1)
                .setId(501L).setAccountId(11L).setStudentPersonId(20L));
        DirectorFormTemplateVO.Snapshot snapshot = positioningSnapshot(java.util.Map.of());
        snapshot.setFields(java.util.List.of());
        when(directorFormTemplateService.validateAndSnapshot(DirectorFormTemplateService.SCENE_POSITIONING,
                null, java.util.Map.of(), false)).thenReturn(snapshot);
        when(directorFormTemplateService.validateAndSnapshotVersion(DirectorFormTemplateService.SCENE_POSITIONING,
                41L, java.util.Map.of(), false, java.util.Map.of())).thenReturn(snapshot);
        PositioningCardDO existing = editableDraft(4).setId(701L).setServiceRelationId(30L);
        when(assignmentService.masterId(30L)).thenReturn(existing.getId());
        when(mapper.selectById(existing.getId())).thenReturn(existing);
        when(mapper.overwriteDraftFromImport(existing, 4)).thenReturn(1);
        PositioningCardImportReqVO request = importRequest();
        request.setTargetDraftId(701L); request.setVersion(4);

        cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder.setTenantId(1L);
        try {
            var result = service.importSubmission(request, 99L);
            assertEquals(5, result.getVersion());
        } finally {
            cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder.clear();
        }
        verify(mapper).overwriteDraftFromImport(existing, 4);
    }

    @Test
    void importSubmissionRejectsSourceFromAnotherStudent() {
        mockImportTarget();
        when(submissionMapper.selectById(101L)).thenReturn(importSubmissionSource().setStudentPersonId(21L));
        when(mapper.selectById(501L)).thenReturn(card(false, "operator_feasibility", 1)
                .setId(501L).setAccountId(11L).setStudentPersonId(21L));
        cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder.setTenantId(1L);
        try {
            assertThrows(cn.iocoder.yudao.framework.common.exception.ServiceException.class,
                    () -> service.importSubmission(importRequest(), 99L));
        } finally {
            cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder.clear();
        }
        verifyNoInteractions(directorFormTemplateService);
    }

    @Test
    void createRejectsDifferentContentWhenARecentDraftAlreadyExists() {
        var account = new cn.iocoder.yudao.module.zsjos.dal.dataobject.account.MediaAccountDO()
                .setId(10L).setStudentPersonId(20L).setCreateServiceRelationId(30L).setDirectorUserId(99L);
        account.setTenantId(1L);
        ServiceRelationDO relation = new ServiceRelationDO().setId(30L).setPersonId(20L).setContentDirectorUserId(99L).setStatus("active")
                .setAcceptanceStatus("accepted").setDirectorStage("positioning_ready");
        relation.setTenantId(1L);
        when(personMapper.selectById(20L)).thenReturn(new cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.PersonDO().setId(20L));
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
        when(mapper.selectLatestCreatingDraft(30L, null, 1L)).thenReturn(existing);
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
                .setId(10L).setStudentPersonId(20L).setCreateServiceRelationId(30L).setDirectorUserId(99L);
        account.setTenantId(1L);
        ServiceRelationDO relation = new ServiceRelationDO().setId(30L).setPersonId(20L)
                .setContentDirectorUserId(99L).setStatus("active")
                .setAcceptanceStatus("accepted").setDirectorStage("positioning_ready");
        relation.setTenantId(1L);
        when(personMapper.selectById(20L)).thenReturn(new cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.PersonDO().setId(20L));
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
        when(mapper.selectLatestCreatingDraft(30L, null, 1L)).thenReturn(existing);
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
        when(directorFormTemplateService.requirePublished(eq(DirectorFormTemplateService.SCENE_POSITIONING), any()))
                .thenReturn(new cn.iocoder.yudao.module.zsjos.dal.dataobject.director.DirectorFormTemplateVersionDO().setId(41L));
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
        when(directorFormTemplateService.requirePublished(eq(DirectorFormTemplateService.SCENE_POSITIONING), any()))
                .thenReturn(new cn.iocoder.yudao.module.zsjos.dal.dataobject.director.DirectorFormTemplateVersionDO().setId(41L));
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
    void professionalSubmitAlsoGoesDirectlyToOperatorWithoutStartingBpm() {
        PositioningCardDO card = card(true, MediaWorkflowConstants.POSITIONING_CO_CREATING, 0)
                .setServiceRelationId(30L);
        when(mapper.selectById(1L)).thenReturn(card);
        mockAssignedOperator();
        when(mapper.transitionWithOperator(1L, 0, MediaWorkflowConstants.POSITIONING_CO_CREATING,
                MediaWorkflowConstants.POSITIONING_OPERATOR_FEASIBILITY, 88L)).thenReturn(1);

        service.submitReview(1L, 0, 99L);

        verifyNoInteractions(processInstanceApi, postApi);
        verify(mapper).transitionWithOperator(1L, 0, MediaWorkflowConstants.POSITIONING_CO_CREATING,
                MediaWorkflowConstants.POSITIONING_OPERATOR_FEASIBILITY, 88L);
        verify(submissionMapper).insert(argThat((PositioningCardSubmissionDO value) ->
                Boolean.TRUE.equals(value.getProfessionalRisk())
                        && MediaWorkflowConstants.POSITIONING_OPERATOR_FEASIBILITY.equals(value.getStatus())));
    }

    @Test
    void startRevisionRestoresEffectiveSnapshotForOriginalDirector() {
        PositioningCardDO card = card(false, MediaWorkflowConstants.POSITIONING_CONFIRMED, 5)
                .setDirectorUserId(99L).setVersionNo(2);
        PositioningCardSubmissionDO effective = submission(15L, MediaWorkflowConstants.POSITIONING_CONFIRMED, 3)
                .setTemplateId(40L).setTemplateVersionId(41L).setValuesSnapshotJson("{\"persona\":\"expert\"}");
        when(mapper.selectByIdForUpdate(1L, 7L)).thenReturn(card);
        when(submissionMapper.selectLatestConfirmedByCard(1L)).thenReturn(effective);
        when(submissionMapper.selectLatestByCard(1L)).thenReturn(effective);
        when(mapper.startRevision(card, effective, 5)).thenReturn(1);

        cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder.setTenantId(7L);
        try {
            PositioningCardDraftRespVO result = service.startRevision(1L, 5, 99L);
            assertEquals(1L, result.getId());
            assertEquals(6, result.getVersion());
        } finally {
            cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder.clear();
        }
        verify(mapper).startRevision(card, effective, 5);
    }

    @Test
    void startRevisionRejectsAnotherDirectorWithoutMutation() {
        PositioningCardDO card = card(false, MediaWorkflowConstants.POSITIONING_CONFIRMED, 5)
                .setDirectorUserId(99L);
        when(mapper.selectByIdForUpdate(1L, 7L)).thenReturn(card);

        cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder.setTenantId(7L);
        try {
            assertThrows(cn.iocoder.yudao.framework.common.exception.ServiceException.class,
                    () -> service.startRevision(1L, 5, 100L));
        } finally {
            cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder.clear();
        }
        verify(mapper, never()).startRevision(any(), any(), any());
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
        return new PositioningCardDO().setId(1L).setAccountId(10L).setStudentPersonId(20L).setServiceRelationId(30L)
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
        relation.setTenantId(1L);
        when(relationMapper.selectById(30L)).thenReturn(relation);
        AdminUserRespDTO operator = new AdminUserRespDTO();
        operator.setId(88L); operator.setStatus(0);
        when(adminUserApi.getUser(88L)).thenReturn(operator);
    }

    private PositioningCardSubmissionDO submission(Long id, String status, Integer version) {
        return new PositioningCardSubmissionDO().setId(id).setCardId(1L).setOperatorUserId(88L)
                .setServiceRelationId(30L)
                .setStatus(status).setVersion(version).setSubmissionNo(1);
    }

    private ServiceRelationDO importRelation() {
        var relation=new ServiceRelationDO().setId(30L).setPersonId(20L).setContentDirectorUserId(99L)
                .setOperatorUserId(88L).setStatus("active").setAcceptanceStatus("accepted")
                .setDirectorStage("positioning_interview_completed");
        relation.setTenantId(1L);return relation;
    }

    private void mockImportTarget() {
        when(personMapper.selectById(20L)).thenReturn(
                new cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.PersonDO().setId(20L));
        ServiceRelationDO relation = importRelation();
        when(relationMapper.selectByIdForUpdate(30L, 1L)).thenReturn(relation);
    }

    private PositioningCardImportReqVO importRequest() {
        PositioningCardImportReqVO request = new PositioningCardImportReqVO();
        request.setSourceSubmissionId(101L); request.setAccountId(10L); request.setStudentPersonId(20L);
        request.setServiceRelationId(30L); request.setTrialEndDate(java.time.LocalDate.now().plusDays(14));
        return request;
    }

    private PositioningCardSubmissionDO importSubmissionSource() {
        DirectorFormTemplateVO.Field name = field("name", "text", null);
        DirectorFormTemplateVO.Field category = field("category", "select", "positioning_category");
        DirectorFormTemplateVO.Field removed = field("removed", "text", null);
        DirectorFormTemplateVO.Field changed = field("changed", "text", null);
        return submission(101L, "operator_feasibility", 0).setCardId(501L).setAccountId(11L)
                .setStudentPersonId(20L).setFieldsSnapshotJson(
                        cn.iocoder.yudao.framework.common.util.json.JsonUtils.toJsonString(
                                java.util.List.of(name, category, removed, changed)))
                .setValuesSnapshotJson(cn.iocoder.yudao.framework.common.util.json.JsonUtils.toJsonString(
                        java.util.Map.of("name", "复用姓名", "category", "expert", "removed", "旧字段",
                                "changed", "不兼容")))
                .setDictSnapshotJson(cn.iocoder.yudao.framework.common.util.json.JsonUtils.toJsonString(
                        java.util.Map.of("category", java.util.Map.of("value", "expert",
                                "labelSnapshot", "专家型", "dictType", "positioning_category"))));
    }

    private DirectorFormTemplateVO.Field field(String key, String type, String dictType) {
        DirectorFormTemplateVO.Field field = new DirectorFormTemplateVO.Field();
        field.setKey(key); field.setTitle(key); field.setType(type); field.setDictType(dictType);
        field.setEnabled(true); field.setRequired(false); field.setSystemField(false); field.setSort(1);
        return field;
    }

    private DirectorFormTemplateVO.Snapshot positioningSnapshot(java.util.Map<String, Object> values) {
        DirectorFormTemplateVO.Snapshot snapshot = new DirectorFormTemplateVO.Snapshot();
        snapshot.setTemplateId(40L); snapshot.setTemplateVersionId(41L); snapshot.setTemplateVersionNo(1);
        snapshot.setFields(java.util.List.of()); snapshot.setValues(values); snapshot.setDictSnapshots(java.util.Map.of());
        return snapshot;
    }
}
