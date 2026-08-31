package cn.iocoder.yudao.module.zsjos.service.feedback;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.bpm.api.definition.BpmDefinitionReadApi;
import cn.iocoder.yudao.module.bpm.api.definition.dto.BpmProcessDefinitionMetadataRespDTO;
import cn.iocoder.yudao.module.bpm.api.event.BpmProcessInstanceStatusEvent;
import cn.iocoder.yudao.module.bpm.api.task.BpmProcessInstanceApi;
import cn.iocoder.yudao.module.bpm.api.task.dto.BpmProcessInstanceCreateReqDTO;
import cn.iocoder.yudao.module.bpm.enums.task.BpmProcessInstanceStatusEnum;
import cn.iocoder.yudao.module.infra.api.file.FileApi;
import cn.iocoder.yudao.module.system.api.dept.DeptApi;
import cn.iocoder.yudao.module.system.api.dept.dto.DeptRespDTO;
import cn.iocoder.yudao.module.system.api.notify.NotifyBusinessEventApi;
import cn.iocoder.yudao.module.system.api.notify.dto.NotifyBusinessEvent;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import cn.iocoder.yudao.module.system.api.permission.RoleApi;
import cn.iocoder.yudao.module.system.api.permission.dto.RoleRespDTO;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import cn.iocoder.yudao.module.system.api.user.dto.AdminUserRespDTO;
import cn.iocoder.yudao.module.zsjos.controller.admin.feedback.vo.FeedbackActionVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.feedback.vo.FeedbackCreateReqVO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.feedback.FeedbackConfigDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.feedback.FeedbackDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.feedback.FeedbackReplyDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.feedback.FeedbackRoundDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.feedback.FeedbackSurveyDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.PartnerDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.workorder.WorkOrderDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.workorder.WorkOrderHistoryDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.feedback.FeedbackConfigMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.feedback.FeedbackMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.feedback.FeedbackNoDailyCounterMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.feedback.FeedbackReplyMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.feedback.FeedbackRoundMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.feedback.FeedbackSurveyMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.lead.PartnerMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.workorder.WorkOrderHistoryMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.workorder.WorkOrderMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants.FEEDBACK_CHAIRMAN_INVALID;
import static cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants.FEEDBACK_CONFIG_VERSION_CONFLICT;
import static cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants.FEEDBACK_SURVEY_ALREADY_REQUESTED;
import static cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants.FEEDBACK_PERMISSION_DENIED;
import static cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants.FEEDBACK_PROCESS_UNAVAILABLE;
import static cn.iocoder.yudao.module.zsjos.enums.PersonnelConstants.PARTNER_STATUS_ENABLED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FeedbackServiceImplTest {

    @Mock private FeedbackMapper feedbackMapper;
    @Mock private FeedbackRoundMapper roundMapper;
    @Mock private FeedbackReplyMapper replyMapper;
    @Mock private FeedbackSurveyMapper surveyMapper;
    @Mock private FeedbackConfigMapper feedbackConfigMapper;
    @Mock private FeedbackNoDailyCounterMapper counterMapper;
    @Mock private WorkOrderMapper workOrderMapper;
    @Mock private WorkOrderHistoryMapper historyMapper;
    @Mock private FeedbackDynamicFormService dynamicFormService;
    @Mock private BpmDefinitionReadApi definitionReadApi;
    @Mock private BpmProcessInstanceApi processInstanceApi;
    @Mock private AdminUserApi adminUserApi;
    @Mock private DeptApi deptApi;
    @Mock private RoleApi roleApi;
    @Mock private PermissionApi permissionApi;
    @Mock private FileApi fileApi;
    @Mock private PartnerMapper partnerMapper;
    @Mock private NotifyBusinessEventApi notifyBusinessEventApi;
    @InjectMocks private FeedbackServiceImpl service;

    @BeforeEach
    void setUp() {
        TenantContextHolder.setTenantId(1L);
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    void supportCreatePersistsBusinessNumberAndDictionarySnapshot() {
        FeedbackConfigDO config = config(FeedbackConstants.TYPE_SUPPORT, false);
        stubOpenConfig(config);
        FeedbackDynamicFormService.ParsedForm form = parsedForm();
        when(dynamicFormService.requireCompatibleForm(1L, FeedbackConstants.TYPE_SUPPORT, "title"))
                .thenReturn(form);
        when(dynamicFormService.normalizeValues(eq(form), any(), eq(11L))).thenReturn(
                new FeedbackDynamicFormService.NormalizedValues(Map.of(
                        "title", "无法访问内网",
                        "supportType", Map.of("type", "zsjos_feedback_support_type",
                                "value", "network_communication", "label", "网络与通信")), List.of()));
        when(adminUserApi.getUser(11L)).thenReturn(user(11L, "提交人"));
        when(counterMapper.selectReservedValue(anyLong(), any(LocalDate.class), eq(FeedbackConstants.TYPE_SUPPORT)))
                .thenReturn(7L);
        doAnswer(invocation -> {
            ((WorkOrderDO) invocation.getArgument(0)).setId(100L);
            return 1;
        }).when(workOrderMapper).insert(any(WorkOrderDO.class));
        doAnswer(invocation -> {
            ((FeedbackDO) invocation.getArgument(0)).setId(200L);
            return 1;
        }).when(feedbackMapper).insert(any(FeedbackDO.class));

        Long id = service.create(FeedbackConstants.TYPE_SUPPORT, createRequest(0), 11L);

        assertEquals(200L, id);
        ArgumentCaptor<FeedbackDO> feedback = ArgumentCaptor.forClass(FeedbackDO.class);
        verify(feedbackMapper).insert(feedback.capture());
        assertTrue(feedback.getValue().getFeedbackNo().matches("SUP-\\d{8}-0007"));
        assertEquals("network_communication", feedback.getValue().getSupportTypeValue());
        assertEquals("网络与通信", feedback.getValue().getSupportTypeLabelSnapshot());
        assertEquals(FeedbackConstants.STATUS_WAITING, feedback.getValue().getStatus());
        verify(workOrderMapper).insert(any(WorkOrderDO.class));
        ArgumentCaptor<NotifyBusinessEvent> event = ArgumentCaptor.forClass(NotifyBusinessEvent.class);
        verify(notifyBusinessEventApi).publish(event.capture());
        assertEquals(FeedbackConstants.NOTIFY_SCENE_READY_FOR_HANDLING, event.getValue().getSceneCode());
        assertEquals(List.of(21L), event.getValue().getPayload().get("dispatcherUserIds"));
        assertEquals("feedback:200:ready:round:0", event.getValue().getSourceEventKey());
    }

    @Test
    void requirementWithoutApprovalNotifiesDispatchersImmediately() {
        FeedbackConfigDO config = config(FeedbackConstants.TYPE_REQUIREMENT, false);
        stubOpenConfig(config);
        FeedbackDynamicFormService.ParsedForm form = parsedForm();
        when(dynamicFormService.requireCompatibleForm(1L, FeedbackConstants.TYPE_REQUIREMENT, "title"))
                .thenReturn(form);
        when(dynamicFormService.normalizeValues(eq(form), any(), eq(11L))).thenReturn(
                new FeedbackDynamicFormService.NormalizedValues(Map.of("title", "建设新系统"), List.of()));
        when(adminUserApi.getUser(11L)).thenReturn(user(11L, "提交人"));
        when(counterMapper.selectReservedValue(anyLong(), any(LocalDate.class), eq(FeedbackConstants.TYPE_REQUIREMENT)))
                .thenReturn(1L);
        stubInsertedRows();

        service.create(FeedbackConstants.TYPE_REQUIREMENT, createRequest(0), 11L);

        ArgumentCaptor<NotifyBusinessEvent> event = ArgumentCaptor.forClass(NotifyBusinessEvent.class);
        verify(notifyBusinessEventApi).publish(event.capture());
        assertEquals(FeedbackConstants.NOTIFY_SCENE_READY_FOR_HANDLING, event.getValue().getSceneCode());
        assertEquals("feedback:200:ready:round:1", event.getValue().getSourceEventKey());
    }

    @Test
    void bugCreateNotifiesDispatchersImmediately() {
        FeedbackConfigDO config = config(FeedbackConstants.TYPE_BUG, false);
        stubOpenConfig(config);
        FeedbackDynamicFormService.ParsedForm form = parsedForm();
        when(dynamicFormService.requireCompatibleForm(1L, FeedbackConstants.TYPE_BUG, "title"))
                .thenReturn(form);
        when(dynamicFormService.normalizeValues(eq(form), any(), eq(11L))).thenReturn(
                new FeedbackDynamicFormService.NormalizedValues(Map.of("title", "页面无法保存"), List.of()));
        when(adminUserApi.getUser(11L)).thenReturn(user(11L, "提交人"));
        when(workOrderMapper.selectMaxFeedbackNumber(anyLong(), eq(FeedbackConstants.TYPE_BUG), any()))
                .thenReturn(2L);
        when(counterMapper.selectReservedValue(anyLong(), any(LocalDate.class), eq(FeedbackConstants.TYPE_BUG)))
                .thenReturn(3L);
        AtomicReference<FeedbackDO> inserted = stubInsertedRows();

        service.create(FeedbackConstants.TYPE_BUG, createRequest(0), 11L);

        ArgumentCaptor<NotifyBusinessEvent> event = ArgumentCaptor.forClass(NotifyBusinessEvent.class);
        verify(notifyBusinessEventApi).publish(event.capture());
        assertEquals(FeedbackConstants.NOTIFY_SCENE_READY_FOR_HANDLING, event.getValue().getSceneCode());
        assertEquals(List.of(21L), event.getValue().getPayload().get("dispatcherUserIds"));
        assertTrue(inserted.get().getFeedbackNo().matches("BUG-\\d{8}-0003"));
        verify(counterMapper).reserve(anyLong(), any(LocalDate.class), eq(FeedbackConstants.TYPE_BUG), eq(3L));
    }

    @Test
    void partnerBugCreatePersistsPartnerSubjectAndNotifiesDispatchers() {
        FeedbackConfigDO config = config(FeedbackConstants.TYPE_BUG, false);
        stubOpenConfig(config);
        FeedbackDynamicFormService.ParsedForm form = parsedForm();
        when(partnerMapper.selectById(99L)).thenReturn(partner(99L, "合作方甲"));
        when(dynamicFormService.requireCompatibleForm(1L, FeedbackConstants.TYPE_BUG, "title"))
                .thenReturn(form);
        when(dynamicFormService.normalizeValues(eq(form), any(), eq(11L))).thenReturn(
                new FeedbackDynamicFormService.NormalizedValues(Map.of("title", "兼职端页面异常"), List.of()));
        when(counterMapper.selectReservedValue(anyLong(), any(LocalDate.class), eq(FeedbackConstants.TYPE_BUG)))
                .thenReturn(4L);
        stubInsertedRows();

        Long id = service.createForPartner(FeedbackConstants.TYPE_BUG, createRequest(0), 11L, 99L);

        assertEquals(200L, id);
        ArgumentCaptor<WorkOrderDO> workOrder = ArgumentCaptor.forClass(WorkOrderDO.class);
        verify(workOrderMapper).insert(workOrder.capture());
        assertEquals(FeedbackConstants.SUBJECT_PARTNER_ACCOUNT, workOrder.getValue().getSourceSubjectType());
        assertEquals(11L, workOrder.getValue().getSourceUserId());
        assertEquals(FeedbackConstants.SUBJECT_PARTNER_ACCOUNT, workOrder.getValue().getCommandSubjectType());
        assertEquals(11L, workOrder.getValue().getCommandUserId());
        assertEquals("合作方甲", workOrder.getValue().getSourceNameSnapshot());

        ArgumentCaptor<FeedbackDO> feedback = ArgumentCaptor.forClass(FeedbackDO.class);
        verify(feedbackMapper).insert(feedback.capture());
        assertEquals(FeedbackConstants.SUBJECT_PARTNER_ACCOUNT, feedback.getValue().getSubmitterSubjectType());
        assertEquals(11L, feedback.getValue().getSubmitterUserId());
        assertEquals(99L, feedback.getValue().getPartnerId());
        assertEquals("合作方甲", feedback.getValue().getSubmitterNameSnapshot());

        ArgumentCaptor<WorkOrderHistoryDO> history = ArgumentCaptor.forClass(WorkOrderHistoryDO.class);
        verify(historyMapper).insert(history.capture());
        assertEquals(FeedbackConstants.SUBJECT_PARTNER_ACCOUNT, history.getValue().getOperatorSubjectType());
        assertEquals(11L, history.getValue().getOperatorUserId());

        ArgumentCaptor<NotifyBusinessEvent> event = ArgumentCaptor.forClass(NotifyBusinessEvent.class);
        verify(notifyBusinessEventApi).publish(event.capture());
        assertEquals(FeedbackConstants.NOTIFY_SCENE_READY_FOR_HANDLING, event.getValue().getSceneCode());
        assertEquals(FeedbackConstants.SUBJECT_PARTNER_ACCOUNT,
                event.getValue().getPayload().get("submitterSubjectType"));
        assertEquals(99L, event.getValue().getPayload().get("partnerId"));
        assertEquals(List.of(21L), event.getValue().getPayload().get("dispatcherUserIds"));
    }

    @Test
    void partnerRequirementWithApprovalRejectsBeforeCreatingProcess() {
        FeedbackConfigDO config = config(FeedbackConstants.TYPE_REQUIREMENT, true);
        stubOpenConfig(config);
        when(partnerMapper.selectById(99L)).thenReturn(partner(99L, "合作方甲"));

        ServiceException error = assertThrows(ServiceException.class,
                () -> service.createForPartner(FeedbackConstants.TYPE_REQUIREMENT, createRequest(0), 11L, 99L));

        assertEquals(FEEDBACK_PROCESS_UNAVAILABLE.getCode(), error.getCode());
        verify(dynamicFormService, never()).requireCompatibleForm(anyLong(), any(), any());
        verify(processInstanceApi, never()).createProcessInstance(anyLong(), any());
        verify(workOrderMapper, never()).insert(any(WorkOrderDO.class));
        verify(feedbackMapper, never()).insert(any(FeedbackDO.class));
    }

    @Test
    void partnerCannotReadAnotherPartnerFeedback() {
        FeedbackDO row = feedback(FeedbackConstants.TYPE_BUG, FeedbackConstants.STATUS_WAITING);
        row.setSubmitterSubjectType(FeedbackConstants.SUBJECT_PARTNER_ACCOUNT);
        row.setPartnerId(100L);
        when(partnerMapper.selectById(99L)).thenReturn(partner(99L, "合作方甲"));
        when(feedbackMapper.selectById(200L)).thenReturn(row);

        ServiceException error = assertThrows(ServiceException.class,
                () -> service.getPartnerOwn(200L, 11L, 99L));

        assertEquals(FEEDBACK_PERMISSION_DENIED.getCode(), error.getCode());
        verify(replyMapper, never()).selectByFeedbackId(anyLong());
    }

    @Test
    void createRejectsStaleConfigurationBeforeReadingForm() {
        when(feedbackConfigMapper.selectByType(FeedbackConstants.TYPE_BUG))
                .thenReturn(config(FeedbackConstants.TYPE_BUG, false));

        ServiceException error = assertThrows(ServiceException.class,
                () -> service.create(FeedbackConstants.TYPE_BUG, createRequest(9), 11L));

        assertEquals(FEEDBACK_CONFIG_VERSION_CONFLICT.getCode(), error.getCode());
        verify(dynamicFormService, never()).requireCompatibleForm(anyLong(), any(), any());
    }

    @Test
    void assigningWaitingFeedbackMovesItToInProgress() {
        FeedbackDO row = feedback(FeedbackConstants.TYPE_BUG, FeedbackConstants.STATUS_WAITING);
        when(feedbackMapper.selectByIdForUpdate(200L)).thenReturn(row);
        when(permissionApi.getEnabledUserIdsByPermission(FeedbackConstants.PERMISSION_BUG_MANAGE))
                .thenReturn(Set.of(21L));
        when(adminUserApi.getUser(21L)).thenReturn(user(21L, "处理人"));
        when(feedbackMapper.updateById(row)).thenReturn(1);
        when(workOrderMapper.selectById(100L)).thenReturn(workOrder());
        FeedbackActionVO.AssignReq request = new FeedbackActionVO.AssignReq();
        request.setVersion(0);
        request.setIdempotencyKey("assign-1");
        request.setAssigneeUserId(21L);

        service.assign(200L, request, 31L);

        assertEquals(FeedbackConstants.STATUS_IN_PROGRESS, row.getStatus());
        assertEquals(21L, row.getAssigneeUserId());
        verify(historyMapper).insert(any(WorkOrderHistoryDO.class));
    }

    @Test
    void employeeCanReplyAfterCompletionAndNotifiesCurrentAssignee() {
        FeedbackDO row = feedback(FeedbackConstants.TYPE_BUG, FeedbackConstants.STATUS_COMPLETED);
        row.setAssigneeUserId(21L);
        when(feedbackMapper.selectByIdForUpdate(200L)).thenReturn(row);
        when(adminUserApi.getUser(11L)).thenReturn(user(11L, "提交人"));
        when(feedbackMapper.updateById(row)).thenReturn(1);
        when(workOrderMapper.selectById(100L)).thenReturn(workOrder());
        stubOpenConfig(config(FeedbackConstants.TYPE_BUG, false));
        FeedbackActionVO.ReplyReq request = new FeedbackActionVO.ReplyReq();
        request.setVersion(0);
        request.setIdempotencyKey("reply-1");
        request.setContent("补充一张复现截图");
        request.setAttachmentIds(List.of());

        service.replyOwn(200L, request, 11L);

        assertTrue(row.getUnreadForAssignee());
        verify(replyMapper).insert(any(FeedbackReplyDO.class));
        ArgumentCaptor<NotifyBusinessEvent> event = ArgumentCaptor.forClass(NotifyBusinessEvent.class);
        verify(notifyBusinessEventApi).publish(event.capture());
        assertEquals(21L, event.getValue().getPayload().get("assigneeUserId"));
    }

    @Test
    void completedFeedbackCanRequestSurveyOnlyOnce() {
        FeedbackDO row = feedback(FeedbackConstants.TYPE_BUG, FeedbackConstants.STATUS_COMPLETED);
        when(feedbackMapper.selectByIdForUpdate(200L)).thenReturn(row);
        when(surveyMapper.selectByFeedbackId(200L)).thenReturn(new FeedbackSurveyDO());
        FeedbackActionVO.VersionedCommand request = new FeedbackActionVO.VersionedCommand();
        request.setVersion(0);
        request.setIdempotencyKey("survey-1");

        ServiceException error = assertThrows(ServiceException.class,
                () -> service.requestSurvey(200L, request, 31L));

        assertEquals(FEEDBACK_SURVEY_ALREADY_REQUESTED.getCode(), error.getCode());
        verify(surveyMapper, never()).insert(any(FeedbackSurveyDO.class));
    }

    @Test
    void requirementWithoutDepartmentLeaderStartsDirectlyWithChairman() {
        FeedbackConfigDO config = config(FeedbackConstants.TYPE_REQUIREMENT, true);
        stubOpenConfig(config);
        stubRequirementForm(config);
        AdminUserRespDTO submitter = user(11L, "提交人");
        submitter.setDeptId(5L);
        when(adminUserApi.getUser(11L)).thenReturn(submitter);
        when(deptApi.getDept(5L)).thenReturn(new DeptRespDTO());
        stubChairmen(List.of(user(30L, "董事长")));
        when(processInstanceApi.createProcessInstance(eq(11L), any())).thenReturn("pi-1");
        when(counterMapper.selectReservedValue(anyLong(), any(LocalDate.class), eq(FeedbackConstants.TYPE_REQUIREMENT)))
                .thenReturn(1L);
        AtomicReference<FeedbackDO> inserted = stubInsertedRows();
        when(feedbackMapper.selectById(200L)).thenAnswer(invocation -> inserted.get());
        when(feedbackMapper.updateById(any(FeedbackDO.class))).thenReturn(1);
        when(roundMapper.insert(any(FeedbackRoundDO.class))).thenReturn(1);
        when(roundMapper.updateById(any(FeedbackRoundDO.class))).thenReturn(1);

        service.create(FeedbackConstants.TYPE_REQUIREMENT, createRequest(0), 11L);

        ArgumentCaptor<BpmProcessInstanceCreateReqDTO> process =
                ArgumentCaptor.forClass(BpmProcessInstanceCreateReqDTO.class);
        verify(processInstanceApi).createProcessInstance(eq(11L), process.capture());
        assertEquals("feedback:100:round:1", process.getValue().getBusinessKey());
        assertFalse((Boolean) process.getValue().getVariables().get("hasDepartmentLeader"));
        assertEquals(List.of(), process.getValue().getStartUserSelectAssignees()
                .get(FeedbackConstants.TASK_DEPARTMENT_LEADER));
        assertEquals(List.of(30L), process.getValue().getStartUserSelectAssignees()
                .get(FeedbackConstants.TASK_CHAIRMAN));
        verify(notifyBusinessEventApi, never()).publish(any());
    }

    @Test
    void approvedRequirementNotifiesCurrentEligibleDispatchersOnlyOnce() {
        FeedbackDO row = feedback(FeedbackConstants.TYPE_REQUIREMENT, FeedbackConstants.STATUS_APPROVING);
        row.setProcessInstanceId("pi-1");
        row.setApprovalRoundNo(2);
        FeedbackConfigDO config = config(FeedbackConstants.TYPE_REQUIREMENT, true);
        config.setDispatcherUserIdsJson("[21,22]");
        when(feedbackMapper.selectByProcessInstanceId("pi-1")).thenReturn(row);
        when(feedbackMapper.updateById(row)).thenReturn(1);
        when(workOrderMapper.selectById(100L)).thenReturn(workOrder());
        when(feedbackConfigMapper.selectByType(FeedbackConstants.TYPE_REQUIREMENT)).thenReturn(config);
        when(permissionApi.getEnabledUserIdsByPermission(FeedbackConstants.PERMISSION_REQUIREMENT_MANAGE))
                .thenReturn(Set.of(22L, 23L));
        BpmProcessInstanceStatusEvent event = processEvent("pi-1",
                BpmProcessInstanceStatusEnum.APPROVE.getStatus());

        service.handleProcessResult(event);
        service.handleProcessResult(event);

        assertEquals(FeedbackConstants.STATUS_WAITING, row.getStatus());
        ArgumentCaptor<NotifyBusinessEvent> notification = ArgumentCaptor.forClass(NotifyBusinessEvent.class);
        verify(notifyBusinessEventApi, times(1)).publish(notification.capture());
        assertEquals(List.of(22L), notification.getValue().getPayload().get("dispatcherUserIds"));
        assertEquals("feedback:200:ready:round:2", notification.getValue().getSourceEventKey());
        assertEquals(null, notification.getValue().getOperatorUserId());
    }

    @Test
    void rejectedRequirementDoesNotNotifyDispatchers() {
        FeedbackDO row = feedback(FeedbackConstants.TYPE_REQUIREMENT, FeedbackConstants.STATUS_APPROVING);
        row.setProcessInstanceId("pi-1");
        row.setApprovalRoundNo(1);
        when(feedbackMapper.selectByProcessInstanceId("pi-1")).thenReturn(row);
        when(feedbackMapper.updateById(row)).thenReturn(1);
        when(workOrderMapper.selectById(100L)).thenReturn(workOrder());

        service.handleProcessResult(processEvent("pi-1", BpmProcessInstanceStatusEnum.REJECT.getStatus()));

        assertEquals(FeedbackConstants.STATUS_APPROVAL_REJECTED, row.getStatus());
        verify(notifyBusinessEventApi, never()).publish(any());
    }

    @Test
    void resubmittedRequirementWithoutApprovalNotifiesForNewRound() {
        FeedbackDO row = feedback(FeedbackConstants.TYPE_REQUIREMENT,
                FeedbackConstants.STATUS_APPROVAL_REJECTED);
        row.setApprovalRoundNo(1);
        when(feedbackMapper.selectByIdForUpdate(200L)).thenReturn(row);
        FeedbackConfigDO config = config(FeedbackConstants.TYPE_REQUIREMENT, false);
        stubOpenConfig(config);
        FeedbackDynamicFormService.ParsedForm form = parsedForm();
        when(dynamicFormService.requireCompatibleForm(1L, FeedbackConstants.TYPE_REQUIREMENT, "title"))
                .thenReturn(form);
        when(dynamicFormService.normalizeValues(eq(form), any(), eq(11L))).thenReturn(
                new FeedbackDynamicFormService.NormalizedValues(Map.of("title", "调整后的需求"), List.of()));
        when(adminUserApi.getUser(11L)).thenReturn(user(11L, "提交人"));
        when(feedbackMapper.updateById(row)).thenReturn(1);
        when(workOrderMapper.selectById(100L)).thenReturn(workOrder());
        FeedbackActionVO.ResubmitReq request = new FeedbackActionVO.ResubmitReq();
        request.setVersion(0);
        request.setConfigVersion(0);
        request.setIdempotencyKey("resubmit-1");
        request.setValues(Map.of("title", "调整后的需求"));

        service.resubmit(200L, request, 11L);

        assertEquals(FeedbackConstants.STATUS_WAITING, row.getStatus());
        assertEquals(2, row.getApprovalRoundNo());
        ArgumentCaptor<NotifyBusinessEvent> notification = ArgumentCaptor.forClass(NotifyBusinessEvent.class);
        verify(notifyBusinessEventApi).publish(notification.capture());
        assertEquals("feedback:200:ready:round:2", notification.getValue().getSourceEventKey());
    }

    @Test
    void resubmittedRequirementWithApprovalWaitsForProcessResult() {
        FeedbackDO row = feedback(FeedbackConstants.TYPE_REQUIREMENT,
                FeedbackConstants.STATUS_APPROVAL_REJECTED);
        row.setApprovalRoundNo(1);
        when(feedbackMapper.selectByIdForUpdate(200L)).thenReturn(row);
        FeedbackConfigDO config = config(FeedbackConstants.TYPE_REQUIREMENT, true);
        stubOpenConfig(config);
        stubRequirementForm(config);
        when(adminUserApi.getUser(11L)).thenReturn(user(11L, "提交人"));
        stubChairmen(List.of(user(30L, "董事长")));
        when(feedbackMapper.updateById(any(FeedbackDO.class))).thenReturn(1);
        when(feedbackMapper.selectById(200L)).thenReturn(row);
        when(workOrderMapper.selectById(100L)).thenReturn(workOrder());
        when(processInstanceApi.createProcessInstance(eq(11L), any())).thenReturn("pi-2");
        FeedbackActionVO.ResubmitReq request = new FeedbackActionVO.ResubmitReq();
        request.setVersion(0);
        request.setConfigVersion(0);
        request.setIdempotencyKey("resubmit-2");
        request.setValues(Map.of("title", "调整后的需求"));

        service.resubmit(200L, request, 11L);

        assertEquals(FeedbackConstants.STATUS_APPROVING, row.getStatus());
        assertEquals(2, row.getApprovalRoundNo());
        verify(notifyBusinessEventApi, never()).publish(any());
    }

    @Test
    void requirementSubmissionRejectsAmbiguousChairmanConfiguration() {
        FeedbackConfigDO config = config(FeedbackConstants.TYPE_REQUIREMENT, true);
        stubOpenConfig(config);
        stubRequirementForm(config);
        when(adminUserApi.getUser(11L)).thenReturn(user(11L, "提交人"));
        stubChairmen(List.of(user(30L, "董事长甲"), user(31L, "董事长乙")));
        when(counterMapper.selectReservedValue(anyLong(), any(LocalDate.class), eq(FeedbackConstants.TYPE_REQUIREMENT)))
                .thenReturn(1L);
        stubInsertedRows();

        ServiceException error = assertThrows(ServiceException.class,
                () -> service.create(FeedbackConstants.TYPE_REQUIREMENT, createRequest(0), 11L));

        assertEquals(FEEDBACK_CHAIRMAN_INVALID.getCode(), error.getCode());
        verify(processInstanceApi, never()).createProcessInstance(anyLong(), any());
    }

    private void stubRequirementForm(FeedbackConfigDO config) {
        FeedbackDynamicFormService.ParsedForm form = parsedForm();
        when(dynamicFormService.requireCompatibleForm(1L, FeedbackConstants.TYPE_REQUIREMENT, "title"))
                .thenReturn(form);
        when(dynamicFormService.normalizeValues(eq(form), any(), eq(11L))).thenReturn(
                new FeedbackDynamicFormService.NormalizedValues(Map.of("title", "建设新系统"), List.of()));
        BpmProcessDefinitionMetadataRespDTO definition = new BpmProcessDefinitionMetadataRespDTO();
        definition.setId("definition-1");
        definition.setKey(FeedbackConstants.PROCESS_DEFINITION_KEY);
        definition.setVersion(1);
        definition.setSuspended(false);
        when(definitionReadApi.getPublishedProcessDefinition(config.getBpmProcessDefinitionKey()))
                .thenReturn(definition);
    }

    private void stubChairmen(List<AdminUserRespDTO> users) {
        RoleRespDTO role = new RoleRespDTO();
        role.setId(9L);
        role.setCode(FeedbackConstants.ROLE_CHAIRMAN);
        role.setStatus(0);
        when(roleApi.getRoleByCode(FeedbackConstants.ROLE_CHAIRMAN)).thenReturn(role);
        Set<Long> ids = users.stream().map(AdminUserRespDTO::getId).collect(java.util.stream.Collectors.toSet());
        when(permissionApi.getUserRoleIdListByRoleIds(Set.of(9L))).thenReturn(ids);
        when(adminUserApi.getUserList(ids)).thenReturn(users);
    }

    private AtomicReference<FeedbackDO> stubInsertedRows() {
        AtomicReference<FeedbackDO> inserted = new AtomicReference<>();
        doAnswer(invocation -> {
            ((WorkOrderDO) invocation.getArgument(0)).setId(100L);
            return 1;
        }).when(workOrderMapper).insert(any(WorkOrderDO.class));
        doAnswer(invocation -> {
            FeedbackDO row = invocation.getArgument(0);
            row.setId(200L);
            inserted.set(row);
            return 1;
        }).when(feedbackMapper).insert(any(FeedbackDO.class));
        return inserted;
    }

    private void stubOpenConfig(FeedbackConfigDO config) {
        when(feedbackConfigMapper.selectByType(config.getFeedbackType())).thenReturn(config);
        when(permissionApi.getEnabledUserIdsByPermission(
                FeedbackConstants.TYPE_PERMISSION.get(config.getFeedbackType()))).thenReturn(Set.of(21L));
    }

    private FeedbackDynamicFormService.ParsedForm parsedForm() {
        return new FeedbackDynamicFormService.ParsedForm(1L, "反馈表单", List.of(), List.of(),
                List.of("title"), List.of());
    }

    private FeedbackConfigDO config(String type, boolean approvalEnabled) {
        FeedbackConfigDO config = new FeedbackConfigDO();
        config.setId(1L);
        config.setFeedbackType(type);
        config.setFormId(1L);
        config.setTitleFieldKey("title");
        config.setDispatcherUserIdsJson("[21]");
        config.setApprovalEnabled(approvalEnabled);
        config.setBpmProcessDefinitionKey(approvalEnabled ? FeedbackConstants.PROCESS_DEFINITION_KEY : null);
        config.setVersion(0);
        return config;
    }

    private FeedbackCreateReqVO createRequest(int configVersion) {
        FeedbackCreateReqVO request = new FeedbackCreateReqVO();
        request.setValues(Map.of("title", "测试反馈"));
        request.setConfigVersion(configVersion);
        request.setIdempotencyKey("create-1");
        return request;
    }

    private BpmProcessInstanceStatusEvent processEvent(String id, Integer status) {
        BpmProcessInstanceStatusEvent event = new BpmProcessInstanceStatusEvent(this);
        event.setId(id);
        event.setProcessDefinitionKey(FeedbackConstants.PROCESS_DEFINITION_KEY);
        event.setStatus(status);
        return event;
    }

    private FeedbackDO feedback(String type, String status) {
        FeedbackDO row = new FeedbackDO();
        row.setId(200L);
        row.setWorkOrderId(100L);
        row.setFeedbackType(type);
        row.setFeedbackNo("BUG-20260826-0001");
        row.setTitle("测试反馈");
        row.setStatus(status);
        row.setSubmitterSubjectType(FeedbackConstants.SUBJECT_ADMIN);
        row.setSubmitterUserId(11L);
        row.setVersion(0);
        return row;
    }

    private PartnerDO partner(Long id, String name) {
        PartnerDO partner = new PartnerDO();
        partner.setId(id);
        partner.setName(name);
        partner.setStatus(PARTNER_STATUS_ENABLED);
        return partner;
    }

    private WorkOrderDO workOrder() {
        WorkOrderDO row = new WorkOrderDO();
        row.setId(100L);
        row.setBusinessType(FeedbackConstants.BUSINESS_TYPE_FEEDBACK);
        row.setVersion(0);
        return row;
    }

    private AdminUserRespDTO user(Long id, String name) {
        AdminUserRespDTO user = new AdminUserRespDTO();
        user.setId(id);
        user.setNickname(name);
        user.setStatus(0);
        return user;
    }
}
