package cn.iocoder.yudao.module.zsjos.service.studentcontact;

import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import cn.iocoder.yudao.module.zsjos.controller.admin.registration.vo.StudentContactContextRespVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.registration.vo.StudentContactSubmitReqVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.registration.vo.StudentFirstContactSubmitReqVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.registration.vo.StudentBasicInfoUpdateReqVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.registration.vo.StudentDeliveryStageSubmitReqVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.registration.vo.StudentServiceAcceptReqVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.registration.vo.StudentStudyPlanSubmitReqVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.registration.vo.DirectorStageSaveReqVO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.event.BusinessEventDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.PersonDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.registration.ServiceRelationDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.registration.StudentContactConfigVersionDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.registration.StudentContactRecordDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.task.BusinessTaskDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.registration.ServiceRelationMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.registration.StudentContactConfigVersionMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.registration.StudentContactRecordMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.event.BusinessEventMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.lead.PersonMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.task.BusinessTaskMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.order.SalesOrderMapper;
import cn.iocoder.yudao.module.zsjos.service.lead.PersonIdentityWriteService;
import cn.iocoder.yudao.module.zsjos.service.task.BusinessTaskCommandService;
import cn.iocoder.yudao.module.zsjos.service.director.DirectorConfigService;
import cn.iocoder.yudao.module.zsjos.service.director.DirectorFormTemplateService;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.common.pojo.PageParam;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import cn.iocoder.yudao.module.zsjos.controller.admin.registration.vo.StudentCollaboratorAssignReqVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.registration.vo.StudyPlannerSimpleRespVO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.registration.StudentCollaboratorAssignmentLogMapper;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.registration.StudentCollaboratorAssignmentLogDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.positioning.PositioningCardMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.account.MediaAccountMapper;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.account.MediaAccountDO;

import java.time.LocalDateTime;
import java.util.List;
import java.util.HashMap;
import java.util.Map;

import static cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants.*;
import static cn.iocoder.yudao.module.zsjos.service.studentcontact.StudentContactConstants.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class StudentContactServiceImplTest {
    @org.mockito.Mock private cn.iocoder.yudao.module.zsjos.service.media.MediaCollaborationNotifyPublisher collaborationNotify;

    @InjectMocks private StudentContactServiceImpl service;
    @Mock private ServiceRelationMapper relationMapper;
    @Mock private StudentCollaboratorAssignmentLogMapper assignmentLogMapper;
    @Mock private PositioningCardMapper positioningCardMapper;
    @Mock private MediaAccountMapper mediaAccountMapper;
    @Mock private StudentContactNotifyPublisher studentContactNotifyPublisher;
    @Mock private cn.iocoder.yudao.module.zsjos.dal.mysql.positioninginterview.PositioningInterviewMapper positioningInterviewMapper;
    @Mock private StudentContactConfigVersionMapper configMapper;
    @Mock private StudentContactRecordMapper recordMapper;
    @Mock private BusinessTaskMapper taskMapper;
    @Mock private BusinessTaskCommandService taskCommandService;
    @Mock private SalesOrderMapper orderMapper;
    @Mock private PermissionApi permissionApi;
    @Mock private cn.iocoder.yudao.module.zsjos.dal.mysql.registration.StudentContactExtensionMapper extensionMapper;
    @Mock private cn.iocoder.yudao.module.zsjos.service.registration.StudentServiceObjectPermissionProvider serviceReadPermission;
    @Mock private cn.iocoder.yudao.module.zsjos.service.studentinfo.StudentInfoPermissionProvider studentInfoPermission;
    @Mock private AdminUserApi adminUserApi;
    @Mock private StudentContactConfigService configService;
    @Mock private PersonMapper personMapper;
    @Mock private PersonIdentityWriteService personIdentityWriteService;
    @Mock private BusinessEventMapper eventMapper;
    @Mock private DirectorConfigService directorConfigService;
    @Mock private DirectorFormTemplateService directorFormTemplateService;

    @Test void administratorReadsExtensionHistoryWithoutAssumingReviewerIdentity() {
        var page = new cn.iocoder.yudao.framework.common.pojo.PageParam();
        when(permissionApi.hasTenantReadAllAccess(9L)).thenReturn(true);
        when(extensionMapper.selectReadPage(page, 9L, "history", true))
                .thenReturn(cn.iocoder.yudao.framework.common.pojo.PageResult.empty());
        assertEquals(0L, service.getExtensions(page, "history", 9L).getTotal());
        verify(extensionMapper).selectReadPage(page, 9L, "history", true);
        verifyNoInteractions(taskCommandService);
    }

    @BeforeEach
    void grantDeliveryStagePermissionForServiceContractTests() {
        lenient().when(permissionApi.hasAnyPermissions(anyLong(), eq("zsjos:student-info-form:read"))).thenReturn(false);
        lenient().when(permissionApi.hasAnyPermissions(anyLong(), eq(PERMISSION_DELIVERY_STAGE_SUBMIT))).thenReturn(true);
        lenient().when(directorConfigService.interviewAppointmentHours()).thenReturn(96);
        lenient().when(directorConfigService.trialDays()).thenReturn(14);
    }

    @AfterEach
    void clearTenant() {
        TenantContextHolder.clear();
    }

    @Test
    void emptyLegacyInterviewDoesNotRequireAnActiveQuestionnaireTemplate() {
        prepareContext();
        StudentContactContextRespVO result = service.getContext(10L, 7L);
        assertEquals("empty", result.getDirectorForms().getInterview().getState());
        assertEquals(List.of(), result.getDirectorForms().getInterview().getFields());
        verifyNoInteractions(directorFormTemplateService);
    }

    @Test
    void retiredInterviewSnapshotRemainsReadableWithoutLiveTemplate() {
        prepareContext();
        ServiceRelationDO relation = relation("accepted");
        relation.setDirectorInterviewSnapshotJson("{\"fields\":[],\"values\":{\"legacy\":\"stored value\"},\"templateVersionId\":11}");
        when(relationMapper.selectById(10L)).thenReturn(relation);
        StudentContactContextRespVO result = service.getContext(10L, 7L);
        assertEquals("submitted", result.getDirectorForms().getInterview().getState());
        assertEquals("stored value", result.getDirectorForms().getInterview().getValues().get("legacy"));
        assertEquals(11L, result.getDirectorForms().getInterview().getTemplateVersionId());
        verifyNoInteractions(directorFormTemplateService);
    }

    @Test
    void getContextProjectsOnlyAuthorizedOwnerActions() {
        prepareContext();
        when(permissionApi.hasAnyPermissions(7L, PERMISSION_FIRST_CONTACT_SUBMIT)).thenReturn(true);
        when(permissionApi.hasAnyPermissions(7L, PERMISSION_UPDATE_BASIC_INFO)).thenReturn(true);
        lenient().when(permissionApi.hasAnyPermissions(7L, PERMISSION_COLLABORATOR_ASSIGN)).thenReturn(true);

        StudentContactContextRespVO result = service.getContext(10L, 7L);

        assertEquals(List.of(CONTEXT_ACTION_FIRST_CONTACT, CONTEXT_ACTION_EDIT_BASIC_INFO,
                        CONTEXT_ACTION_ASSIGN_CONTENT_DIRECTOR, CONTEXT_ACTION_ASSIGN_CAREER_PLANNER),
                result.getAvailableActions());
    }

    @Test
    void getContextOmitsActionsWithoutFeaturePermissions() {
        prepareContext();

        StudentContactContextRespVO result = service.getContext(10L, 7L);

        assertEquals(List.of(), result.getAvailableActions());
    }

    @Test
    void completedPositioningInterviewProjectsMediaAccountCreationForDirector() {
        prepareContext();
        ServiceRelationDO relation = relation("accepted");
        relation.setPersonId(100L); relation.setContentDirectorUserId(7L);
        relation.setDirectorStage("positioning_interview_completed");
        when(relationMapper.selectById(10L)).thenReturn(relation);
        lenient().when(permissionApi.hasAnyPermissions(7L, PERMISSION_MEDIA_ACCOUNT_CREATE)).thenReturn(true);

        StudentContactContextRespVO result = service.getContext(10L, 7L);

        assertTrue(result.getAvailableActions().contains(CONTEXT_ACTION_CREATE_MEDIA_ACCOUNT));
    }

    @Test
    void completedInterviewProjectsCreationForAssignedOperatorOnlyWithFeaturePermission() {
        prepareContext();
        ServiceRelationDO relation = relation("accepted");
        relation.setPersonId(100L); relation.setContentDirectorUserId(8L); relation.setOperatorUserId(7L);
        relation.setDirectorStage("positioning_interview_completed");
        when(relationMapper.selectById(10L)).thenReturn(relation);
        lenient().when(permissionApi.hasAnyPermissions(7L, PERMISSION_MEDIA_ACCOUNT_CREATE)).thenReturn(true);
        assertTrue(service.getContext(10L, 7L).getAvailableActions().contains(CONTEXT_ACTION_CREATE_MEDIA_ACCOUNT));
        lenient().when(permissionApi.hasAnyPermissions(7L, PERMISSION_MEDIA_ACCOUNT_CREATE)).thenReturn(false);
        assertFalse(service.getContext(10L, 7L).getAvailableActions().contains(CONTEXT_ACTION_CREATE_MEDIA_ACCOUNT));
        relation.setOperatorUserId(9L);
        assertFalse(service.getContext(10L, 7L).getAvailableActions().contains(CONTEXT_ACTION_CREATE_MEDIA_ACCOUNT));
    }

    @Test
    void collectionTabRequiresFeaturePermissionAndLeadVisibility() {
        prepareContext();
        ServiceRelationDO relation = relation("accepted"); relation.setOrderId(100L);
        when(relationMapper.selectById(10L)).thenReturn(relation);
        when(permissionApi.hasAnyPermissions(7L, "zsjos:student-info-form:read")).thenReturn(true);
        var order = new cn.iocoder.yudao.module.zsjos.dal.dataobject.order.SalesOrderDO(); order.setLeadId(5L);
        when(orderMapper.selectById(100L)).thenReturn(order);
        when(studentInfoPermission.hasPermission(5L, "read", 7L)).thenReturn(true);
        assertEquals(true, service.getContext(10L, 7L).getVisibleTabs().contains("student-info"));
        when(studentInfoPermission.hasPermission(5L, "read", 7L)).thenReturn(false);
        assertFalse(service.getContext(10L, 7L).getVisibleTabs().contains("student-info"));
    }

    @Test
    void getContextBeforeAcceptanceProjectsOnlyAccept() {
        ServiceRelationDO relation = relation("pending");
        lenient().when(relationMapper.selectById(10L)).thenReturn(relation);
        when(configService.requirePublished()).thenReturn(config());
        when(permissionApi.hasAnyPermissions(7L, PERMISSION_ACCEPT)).thenReturn(true);
        lenient().when(adminUserApi.getUserMap(List.of(-1L, -1L))).thenReturn(new HashMap<>());

        StudentContactContextRespVO result = service.getContext(10L, 7L);

        assertEquals(List.of(CONTEXT_ACTION_ACCEPT), result.getAvailableActions());
    }

    @Test
    void getContextProjectsStageSpecificAction() {
        prepareContext(TYPE_STUDY_PLAN);
        when(permissionApi.hasAnyPermissions(7L, PERMISSION_STUDY_PLAN_SUBMIT)).thenReturn(true);

        assertEquals(List.of(CONTEXT_ACTION_STUDY_PLAN), service.getContext(10L, 7L).getAvailableActions());
    }

    @Test
    void getContextProjectsFollowUpAndHidesAssignedCollaborators() {
        prepareContext(TYPE_CONTACT);
        when(permissionApi.hasAnyPermissions(7L, PERMISSION_CONTACT_SUBMIT)).thenReturn(true);
        lenient().when(permissionApi.hasAnyPermissions(7L, PERMISSION_COLLABORATOR_ASSIGN)).thenReturn(true);
        ServiceRelationDO relation = relation("accepted");
        relation.setContentDirectorUserId(51L); relation.setCareerPlannerUserId(52L);
        when(relationMapper.selectById(10L)).thenReturn(relation);
        when(adminUserApi.getUserMap(List.of(7L, 51L, 52L, -1L))).thenReturn(new HashMap<>());

        assertEquals(List.of(CONTEXT_ACTION_FOLLOW_UP, CONTEXT_ACTION_ASSIGN_CONTENT_DIRECTOR,
                CONTEXT_ACTION_ASSIGN_CAREER_PLANNER), service.getContext(10L, 7L).getAvailableActions());
    }

    @Test
    void assignedOperatorReadsConfiguredContextAndHistoryWithoutPlannerActions() {
        ServiceRelationDO relation = relation("accepted");
        relation.setOperatorUserId(9L);
        when(relationMapper.selectById(10L)).thenReturn(relation);
        StudentContactConfigVersionDO config = config();
        config.setCollaboratorTabsJson("{\"operator\":[\"contacts\"]}");
        when(configService.requirePublished()).thenReturn(config);
        when(adminUserApi.getUserMap(anyList())).thenReturn(new HashMap<>());
        when(recordMapper.selectPageByRelationId(any(PageParam.class), eq(10L)))
                .thenReturn(new PageResult<>(List.of(), 0L));

        for (String status : List.of("active", "paused", "completed")) {
            relation.setStatus(status);
            StudentContactContextRespVO context = service.getContext(10L, 9L);
            assertEquals(List.of("overview", "contacts"), context.getVisibleTabs());
            assertEquals(List.of(), context.getAvailableActions());
        }
        assertEquals(0L, service.getRecords(10L, new PageParam(), 9L).getTotal());
    }

    @Test
    void unrelatedUserCannotReadStudentContactContext() {
        when(relationMapper.selectById(10L)).thenReturn(relation("accepted"));

        ServiceException error = assertThrows(ServiceException.class,
                () -> service.getContext(10L, 9L));

        assertEquals(STUDENT_PERMISSION_DENIED.getCode(), error.getCode());
        verifyNoInteractions(configService);
    }

    @Test
    void assignedOperatorCannotExecutePlannerContactCommands() {
        TenantContextHolder.setTenantId(1L);
        ServiceRelationDO relation = relation("accepted");
        relation.setOperatorUserId(9L);
        when(relationMapper.selectByIdForUpdate(10L, 1L)).thenReturn(relation);

        assertPermissionDenied(() -> service.accept(10L, new StudentServiceAcceptReqVO(), 9L));
        assertPermissionDenied(() -> service.submitFirstContact(10L, new StudentFirstContactSubmitReqVO(), 9L));
        assertPermissionDenied(() -> service.submitStudyPlan(10L, new StudentStudyPlanSubmitReqVO(), 9L));
        assertPermissionDenied(() -> service.submitContact(10L, new StudentContactSubmitReqVO(), 9L));
        assertPermissionDenied(() -> service.updateBasicInfo(10L, basicInfoRequest("13800000000"), 9L));
        verifyNoInteractions(recordMapper, personIdentityWriteService, eventMapper);
    }

    @Test
    void nextTaskTypeKeepsFailuresAndAdvancesSuccesses() {
        assertEquals(TYPE_FIRST_CONTACT, ReflectionTestUtils.invokeMethod(service, "nextType", TYPE_FIRST_CONTACT, false));
        assertEquals(TYPE_STUDY_PLAN, ReflectionTestUtils.invokeMethod(service, "nextType", TYPE_FIRST_CONTACT, true));
        assertEquals(TYPE_STUDY_PLAN, ReflectionTestUtils.invokeMethod(service, "nextType", TYPE_STUDY_PLAN, false));
        assertEquals(TYPE_CONTACT, ReflectionTestUtils.invokeMethod(service, "nextType", TYPE_STUDY_PLAN, true));
        assertEquals(TYPE_CONTACT, ReflectionTestUtils.invokeMethod(service, "nextType", TYPE_CONTACT, true));
    }

    @Test
    void deliveryStageProjectionIsOrderedAndOnlyCurrentStageIsAvailable() {
        List<StudentContactContextRespVO.DeliveryStageVO> stages = ReflectionTestUtils.invokeMethod(
                service, "deliveryStages", STAGE_SUPERVISION, true);

        assertEquals(9, stages.size());
        int currentIndex = java.util.stream.IntStream.range(0, stages.size())
                .filter(index -> STAGE_SUPERVISION.equals(stages.get(index).getCode()))
                .findFirst().orElseThrow();
        assertEquals("current", stages.get(currentIndex).getStatus());
        assertEquals(true, stages.get(currentIndex).getAvailable());
        assertEquals("done", stages.get(0).getStatus());
        assertEquals("pending", stages.get(currentIndex + 1).getStatus());

        List<StudentContactContextRespVO.DeliveryStageVO> completed = ReflectionTestUtils.invokeMethod(
                service, "deliveryStages", STAGE_COMPLETED, true);
        assertEquals(false, completed.get(completed.size() - 1).getAvailable());

        List<StudentContactContextRespVO.DeliveryStageVO> unauthorized = ReflectionTestUtils.invokeMethod(
                service, "deliveryStages", STAGE_SUPERVISION, false);
        assertEquals(false, unauthorized.get(currentIndex).getAvailable());
    }

    @Test
    void taskBackedDeliveryStagesAreNotAvailableToGenericStageSubmission() {
        for (String stage : List.of(STAGE_FIRST_CONTACT, STAGE_STUDY_PLAN)) {
            List<StudentContactContextRespVO.DeliveryStageVO> stages = ReflectionTestUtils.invokeMethod(
                    service, "deliveryStages", stage, true);
            StudentContactContextRespVO.DeliveryStageVO current = stages.stream()
                    .filter(item -> stage.equals(item.getCode()))
                    .findFirst().orElseThrow();

            assertEquals("current", current.getStatus());
            assertFalse(current.getAvailable());
        }
    }

    @Test
    void deliveryStageProjectionDoesNotContainRetiredGroupHandoff() {
        List<StudentContactContextRespVO.DeliveryStageVO> stages = ReflectionTestUtils.invokeMethod(
                service, "deliveryStages", STAGE_SUPERVISION, true);

        assertFalse(stages.stream().anyMatch(item -> "group_handoff".equals(item.getCode())));
    }

    @Test
    void submitDeliveryStageRejectsTaskBackedAndTerminalStages() {
        TenantContextHolder.setTenantId(1L);
        for (String stage : List.of(STAGE_FIRST_CONTACT, STAGE_STUDY_PLAN, "group_handoff", STAGE_COMPLETED)) {
            ServiceRelationDO relation = relation("accepted"); relation.setDeliveryStage(stage);
            when(relationMapper.selectByIdForUpdate(10L, 1L)).thenReturn(relation);
            when(recordMapper.selectByIdempotencyKey(stage + "-key")).thenReturn(null);

            ServiceException error = assertThrows(ServiceException.class,
                    () -> service.submitDeliveryStage(10L, deliveryRequest(stage, stage + "-key"), 7L));

            assertEquals(STUDENT_CONTACT_TASK_INVALID.getCode(), error.getCode());
        }
        verify(recordMapper, never()).insert(any(StudentContactRecordDO.class));
    }

    @Test
    void submitDeliveryStageReplaysBeforeCurrentStageValidation() {
        TenantContextHolder.setTenantId(1L);
        ServiceRelationDO relation = relation("accepted"); relation.setDeliveryStage(STAGE_EXAM_PREPARATION);
        when(relationMapper.selectByIdForUpdate(10L, 1L)).thenReturn(relation);
        StudentDeliveryStageSubmitReqVO request = deliveryRequest(STAGE_EXAM_PREPARATION, "replay-key");
        StudentContactRecordDO replay = new StudentContactRecordDO(); replay.setId(99L); replay.setServiceRelationId(10L);
        replay.setRequestFingerprint(ReflectionTestUtils.invokeMethod(service, "deliveryFingerprint", 10L, request));
        when(recordMapper.selectByIdempotencyKey("replay-key")).thenReturn(replay);

        assertEquals(99L, service.submitDeliveryStage(10L, request, 7L));
        verify(relationMapper, never()).advanceDeliveryStage(anyLong(), anyLong(), anyString(), anyString(), any(), anyInt());
    }

    @Test
    void submitDeliveryStageUsesExpectedStageAndVersion() {
        TenantContextHolder.setTenantId(1L);
        ServiceRelationDO relation = relation("accepted"); relation.setDeliveryStage(STAGE_SUPERVISION);
        when(relationMapper.selectByIdForUpdate(10L, 1L)).thenReturn(relation);
        when(recordMapper.selectByIdempotencyKey("stage-key")).thenReturn(null);
        when(relationMapper.advanceDeliveryStage(eq(10L), eq(7L), eq(STAGE_SUPERVISION), eq(STAGE_EXAM_PREPARATION),
                anyString(), eq(2))).thenReturn(1);
        doAnswer(invocation -> {
            StudentContactRecordDO row = invocation.getArgument(0);
            row.setId(100L);
            return 1;
        }).when(recordMapper).insert(any(StudentContactRecordDO.class));

        Long id = service.submitDeliveryStage(10L, deliveryRequest(STAGE_SUPERVISION, "stage-key"), 7L);

        assertEquals(100L, id);
        verify(recordMapper).insert(any(StudentContactRecordDO.class));
        verify(relationMapper).advanceDeliveryStage(eq(10L), eq(7L), eq(STAGE_SUPERVISION),
                eq(STAGE_EXAM_PREPARATION), anyString(), eq(2));
    }

    @Test
    void directorDraftReturnsTheAuthoritativeDraftVersionWithoutAdvancingRelationVersion() {
        TenantContextHolder.setTenantId(1L);
        ServiceRelationDO relation = relation("accepted");
        relation.setContentDirectorUserId(7L); relation.setDirectorStage("precheck");
        when(permissionApi.hasAnyPermissions(7L, PERMISSION_DIRECTOR_PRECHECK)).thenReturn(true);
        when(relationMapper.selectByIdForUpdate(10L, 1L)).thenReturn(relation);
        DirectorStageSaveReqVO request = new DirectorStageSaveReqVO();
        relation.setDirectorPrecheckDraftVersion(2);
        request.setData(Map.of()); request.setVersion(2); request.setIdempotencyKey("draft-key");

        Integer version = service.saveDirectorPrecheckDraft(10L, request, 7L);

        assertEquals(3, version);
        verify(relationMapper).updateById(argThat((ServiceRelationDO row) -> row.getVersion() == 2
                && row.getDirectorPrecheckDraftVersion() == 3
                && row.getDirectorPrecheckDraftJson().contains("draft-key")));
    }

    @Test
    void successfulStudyPlanAdvancesDirectlyToSupervision() {
        TenantContextHolder.setTenantId(1L);
        ServiceRelationDO relation = relation("accepted"); relation.setDeliveryStage(STAGE_STUDY_PLAN);
        BusinessTaskDO task = new BusinessTaskDO();
        task.setId(20L); task.setTaskType(TYPE_STUDY_PLAN); task.setBizType(BIZ_TYPE); task.setBizId(10L);
        task.setAssigneeId(7L); task.setStatus("pending"); task.setPayload("{\"configVersionId\":30}");
        task.setIdempotencyKey("study-task");
        when(relationMapper.selectByIdForUpdate(10L, 1L)).thenReturn(relation);
        when(recordMapper.selectByIdempotencyKey("study-submit")).thenReturn(null);
        when(taskMapper.selectByIdForUpdate(20L, 1L)).thenReturn(task);
        when(configMapper.selectById(30L)).thenReturn(config());
        when(taskCommandService.create(any())).thenReturn(21L);
        when(relationMapper.advanceDeliveryStage(10L, 7L, STAGE_STUDY_PLAN, STAGE_SUPERVISION,
                null, 2)).thenReturn(1);
        doAnswer(invocation -> {
            StudentContactRecordDO row = invocation.getArgument(0);
            row.setId(100L);
            return 1;
        }).when(recordMapper).insert(any(StudentContactRecordDO.class));
        StudentStudyPlanSubmitReqVO request = new StudentStudyPlanSubmitReqVO();
        request.setTaskId(20L); request.setSuccessful(true); request.setRemark("学习计划已确认");
        request.setNextContactAt(LocalDateTime.now().plusHours(1)); request.setAttachmentFileIds(List.of());
        request.setIdempotencyKey("study-submit");

        assertEquals(100L, service.submitStudyPlan(10L, request, 7L));

        verify(relationMapper).advanceDeliveryStage(10L, 7L, STAGE_STUDY_PLAN, STAGE_SUPERVISION,
                null, 2);
    }

    @Test
    void updateBasicInfoWritesPersonAndPiiFreeEvent() {
        TenantContextHolder.setTenantId(1L);
        ServiceRelationDO relation = relation("accepted");
        relation.setPersonId(100L);
        when(relationMapper.selectByIdForUpdate(10L, 1L)).thenReturn(relation);
        PersonDO before = new PersonDO();
        before.setId(100L); before.setName("旧姓名"); before.setMobile("13800000000"); before.setWechatId("old-wechat");
        when(personMapper.selectById(100L)).thenReturn(before);
        StudentBasicInfoUpdateReqVO request = new StudentBasicInfoUpdateReqVO();
        request.setName("新姓名"); request.setMobile("13900000000"); request.setWechatId("new-wechat"); request.setReason("学员要求更正");

        service.updateBasicInfo(10L, request, 7L);

        verify(personIdentityWriteService).update(100L, "新姓名", "13900000000", "new-wechat");
        ArgumentCaptor<BusinessEventDO> captor = ArgumentCaptor.forClass(BusinessEventDO.class);
        verify(eventMapper).insert(captor.capture());
        BusinessEventDO event = captor.getValue();
        assertEquals("student_basic_info_updated", event.getEventType());
        assertEquals("student_service", event.getAggregateType());
        assertEquals(10L, event.getAggregateId());
        assertFalse(event.getRelatedObjectRefs().contains("13900000000"));
        assertFalse(event.getRelatedObjectRefs().contains("new-wechat"));
    }

    @Test
    void updateBasicInfoRejectsUnacceptedService() {
        TenantContextHolder.setTenantId(1L);
        when(relationMapper.selectByIdForUpdate(10L, 1L)).thenReturn(relation("pending"));

        ServiceException error = assertThrows(ServiceException.class,
                () -> service.updateBasicInfo(10L, basicInfoRequest("13800000000"), 7L));

        assertEquals(STUDENT_SERVICE_NOT_ACCEPTED.getCode(), error.getCode());
        verifyNoInteractions(personIdentityWriteService, eventMapper);
    }

    @Test
    void updateBasicInfoRejectsNonOwner() {
        TenantContextHolder.setTenantId(1L);
        ServiceRelationDO relation = relation("accepted"); relation.setOwnerUserId(8L);
        when(relationMapper.selectByIdForUpdate(10L, 1L)).thenReturn(relation);

        ServiceException error = assertThrows(ServiceException.class,
                () -> service.updateBasicInfo(10L, basicInfoRequest("13800000000"), 7L));

        assertEquals(STUDENT_PERMISSION_DENIED.getCode(), error.getCode());
        verifyNoInteractions(personIdentityWriteService, eventMapper);
    }

    @Test
    void updateBasicInfoRejectsInvalidMobile() {
        TenantContextHolder.setTenantId(1L);
        ServiceRelationDO relation = relation("accepted"); relation.setPersonId(100L);
        when(relationMapper.selectByIdForUpdate(10L, 1L)).thenReturn(relation);

        ServiceException error = assertThrows(ServiceException.class,
                () -> service.updateBasicInfo(10L, basicInfoRequest("123"), 7L));

        assertEquals(LEAD_MOBILE_INVALID.getCode(), error.getCode());
        verifyNoInteractions(personIdentityWriteService, eventMapper);
    }

    private void prepareContext() {
        prepareContext(TYPE_FIRST_CONTACT);
    }

    private void prepareContext(String taskType) {
        ServiceRelationDO relation = relation("accepted");
        lenient().when(relationMapper.selectById(10L)).thenReturn(relation);

        BusinessTaskDO task = new BusinessTaskDO();
        task.setId(20L);
        task.setTaskType(taskType);
        task.setStatus("pending");
        task.setDueAt(LocalDateTime.now().plusHours(1));
        task.setPayload("{\"configVersionId\":30}");
        when(taskMapper.selectPendingByRelationAndType(eq(10L), anyString()))
                .thenAnswer(invocation -> taskType.equals(invocation.getArgument(1)) ? task : null);

        when(configMapper.selectById(30L)).thenReturn(config());
        lenient().when(adminUserApi.getUserMap(List.of(-1L, -1L))).thenReturn(new HashMap<>());
    }

    private ServiceRelationDO relation(String acceptanceStatus) {
        ServiceRelationDO relation = new ServiceRelationDO();
        relation.setId(10L); relation.setOwnerUserId(7L); relation.setStatus("active");
        relation.setAcceptanceStatus(acceptanceStatus); relation.setVersion(2);
        return relation;
    }

    private StudentContactConfigVersionDO config() {
        StudentContactConfigVersionDO config = new StudentContactConfigVersionDO();
        config.setId(30L); config.setChecklistJson("[]"); config.setQuickNotesJson("[]");
        config.setCollaboratorTabsJson("{}"); config.setFirstContactTimeoutMinutes(120);
        config.setStudyPlanTimeoutMinutes(1440);
        return config;
    }

    private StudentBasicInfoUpdateReqVO basicInfoRequest(String mobile) {
        StudentBasicInfoUpdateReqVO request = new StudentBasicInfoUpdateReqVO();
        request.setName("学员"); request.setMobile(mobile); request.setReason("更正");
        return request;
    }

    private StudentDeliveryStageSubmitReqVO deliveryRequest(String stage, String key) {
        StudentDeliveryStageSubmitReqVO request = new StudentDeliveryStageSubmitReqVO();
        request.setStage(stage); request.setSuccessful(true); request.setRemark("完成阶段");
        request.setData(Map.of());
        request.setIdempotencyKey(key);
        return request;
    }

    private void assertPermissionDenied(org.junit.jupiter.api.function.Executable command) {
        ServiceException error = assertThrows(ServiceException.class, command);
        assertEquals(STUDENT_PERMISSION_DENIED.getCode(), error.getCode());
    }

    @Test
    void managedReaderCanLoadContextAndRecordsWithoutOwnerActions() {
        prepareContext();
        when(serviceReadPermission.hasPermission(10L, "read", 9L)).thenReturn(true);
        PageParam page = new PageParam();
        when(recordMapper.selectPageByRelationId(page, 10L)).thenReturn(PageResult.empty());

        StudentContactContextRespVO context = service.getContext(10L, 9L);
        assertEquals(10L, context.getServiceRelationId());
        assertFalse(context.getAvailableActions().contains(CONTEXT_ACTION_ACCEPT));
        assertFalse(context.getAvailableActions().contains(CONTEXT_ACTION_FIRST_CONTACT));
        assertFalse(context.getAvailableActions().contains(CONTEXT_ACTION_STUDY_PLAN));
        assertEquals(0L, service.getRecords(10L, page, 9L).getTotal());
        verify(serviceReadPermission, times(2)).hasPermission(10L, "read", 9L);
    }

    @Test
    void outOfScopeReaderCannotLoadEitherSubresource() {
        when(relationMapper.selectById(10L)).thenReturn(relation("accepted"));
        assertPermissionDenied(() -> service.getContext(10L, 9L));
        assertPermissionDenied(() -> service.getRecords(10L, new PageParam(), 9L));
        verifyNoInteractions(recordMapper, taskMapper, configService);
    }

    @Test
    void managedReadScopeFailureDoesNotExposeContactRecords() {
        when(relationMapper.selectById(10L)).thenReturn(relation("accepted"));
        when(serviceReadPermission.hasPermission(10L, "read", 9L))
                .thenThrow(new IllegalStateException("scope unavailable"));
        assertThrows(IllegalStateException.class, () -> service.getRecords(10L, new PageParam(), 9L));
        verifyNoInteractions(recordMapper);
    }

    private StudentCollaboratorAssignReqVO operatorRequest(String reason) {
        StudentCollaboratorAssignReqVO request = new StudentCollaboratorAssignReqVO();
        request.setCollaboratorType("operator"); request.setUserId(9L); request.setVersion(0);
        request.setCorrectionReason(reason); request.setIdempotencyKey("operator-test");
        return request;
    }

    private ServiceRelationDO operatorRelation(long id, Long operator) {
        ServiceRelationDO row = relation("accepted");
        row.setId(id); row.setPersonId(100L); row.setContentDirectorUserId(7L);
        row.setOperatorUserId(operator); row.setVersion(0);
        return row;
    }

    private StudentContactServiceImpl prepareOperatorAssignment(List<ServiceRelationDO> rows) {
        TenantContextHolder.setTenantId(1L);
        when(relationMapper.selectByIdForUpdate(10L, 1L)).thenReturn(rows.getFirst());
        lenient().when(permissionApi.hasAnyPermissions(7L, PERMISSION_DIRECTOR_OPERATOR_ASSIGN)).thenReturn(true);
        when(relationMapper.selectActiveAcceptedByPersonForUpdate(100L, 1L)).thenReturn(rows);
        StudentContactServiceImpl target = spy(service);
        doReturn(List.of(new StudyPlannerSimpleRespVO(9L, "测试运营")))
                .when(target).getCollaboratorCandidates(10L, "operator", 7L);
        return target;
    }

    @Test
    void operatorChangeRequiresReasonEvenWhenOnlyAnotherServiceHasAnOperator() {
        StudentContactServiceImpl target = prepareOperatorAssignment(List.of(operatorRelation(10L, null), operatorRelation(11L, 8L)));
        for (String reason : new String[]{null, "", "   "}) {
            ServiceException error = assertThrows(ServiceException.class, () -> target.assignCollaborator(10L, operatorRequest(reason), 7L));
            assertEquals(STUDENT_COLLABORATOR_CORRECTION_REASON_REQUIRED.getCode(), error.getCode());
        }
        verify(relationMapper, never()).updateById(any(ServiceRelationDO.class));
        verifyNoInteractions(positioningCardMapper, mediaAccountMapper, collaborationNotify, studentContactNotifyPublisher);
        verify(assignmentLogMapper, never()).insert(any(StudentCollaboratorAssignmentLogDO.class));
    }

    @Test
    void operatorChangeSynchronizesRelationsCardsAccountsAndLogsReason() {
        ServiceRelationDO selected = operatorRelation(10L, 8L), other = operatorRelation(11L, 6L);
        StudentContactServiceImpl target = prepareOperatorAssignment(List.of(selected, other));
        MediaAccountDO account = new MediaAccountDO(); account.setId(30L); account.setVersion(2); account.setOwnerOperatorUserId(8L);
        when(mediaAccountMapper.selectByStudent(100L)).thenReturn(List.of(account));
        when(mediaAccountMapper.updateOwnerOperator(30L, 9L, 2)).thenReturn(1);
        target.assignCollaborator(10L, operatorRequest("工作交接"), 7L);
        assertEquals(9L, selected.getOperatorUserId()); assertEquals(9L, other.getOperatorUserId());
        assertEquals(1, selected.getVersion()); assertEquals(1, other.getVersion());
        verify(positioningCardMapper).updateCurrentOperatorByServiceRelations(List.of(10L, 11L), 9L);
        verify(mediaAccountMapper).updateOwnerOperator(30L, 9L, 2);
        ArgumentCaptor<StudentCollaboratorAssignmentLogDO> logs = ArgumentCaptor.forClass(StudentCollaboratorAssignmentLogDO.class);
        verify(assignmentLogMapper, times(2)).insert(logs.capture());
        assertTrue(logs.getAllValues().stream().allMatch(row -> "工作交接".equals(row.getReason())));
        StudentCollaboratorAssignmentLogDO replay = logs.getAllValues().getFirst();
        when(assignmentLogMapper.selectByIdempotencyKey("operator-test")).thenReturn(replay);
        target.assignCollaborator(10L, operatorRequest("工作交接"), 7L);
        verify(assignmentLogMapper, times(2)).insert(any(StudentCollaboratorAssignmentLogDO.class));
        verify(positioningCardMapper, times(1)).updateCurrentOperatorByServiceRelations(anyList(), eq(9L));
        verify(studentContactNotifyPublisher, times(1)).publish(anyString(), eq(10L), anyString(), isNull(), any(), anyMap());
    }

    @Test
    void firstOperatorAssignmentAndUnchangedOperatorNeedNoReason() {
        ServiceRelationDO selected = operatorRelation(10L, null);
        StudentContactServiceImpl target = prepareOperatorAssignment(List.of(selected));
        target.assignCollaborator(10L, operatorRequest(null), 7L);
        StudentCollaboratorAssignReqVO unchanged = operatorRequest(null); unchanged.setVersion(1); unchanged.setIdempotencyKey("same-operator");
        target.assignCollaborator(10L, unchanged, 7L);
        verify(relationMapper, times(1)).updateById(any(ServiceRelationDO.class));
        assertEquals(1, selected.getVersion());
    }

    @Test
    void operatorAssignmentWithoutPermissionCannotWrite() {
        TenantContextHolder.setTenantId(1L);
        when(relationMapper.selectByIdForUpdate(10L, 1L)).thenReturn(operatorRelation(10L, 8L));
        assertPermissionDenied(() -> service.assignCollaborator(10L, operatorRequest("工作交接"), 7L));
        verifyNoInteractions(assignmentLogMapper, positioningCardMapper, mediaAccountMapper);
        verify(relationMapper, never()).updateById(any(ServiceRelationDO.class));
    }

    @Test
    void operatorAssignmentLocksOnlyRequestedTenantAndRejectsMissingRelation() {
        TenantContextHolder.setTenantId(2L);
        ServiceException error = assertThrows(ServiceException.class, () -> service.assignCollaborator(10L, operatorRequest("工作交接"), 7L));
        assertEquals(STUDENT_SERVICE_NOT_EXISTS.getCode(), error.getCode());
        verify(relationMapper).selectByIdForUpdate(10L, 2L);
        verifyNoInteractions(assignmentLogMapper, positioningCardMapper, mediaAccountMapper);
    }

}
