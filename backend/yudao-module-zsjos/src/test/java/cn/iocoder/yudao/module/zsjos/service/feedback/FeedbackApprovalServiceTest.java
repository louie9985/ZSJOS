package cn.iocoder.yudao.module.zsjos.service.feedback;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.bpm.api.task.BpmProcessProgressApi;
import cn.iocoder.yudao.module.bpm.api.task.dto.BpmProcessProgressDTO;
import cn.iocoder.yudao.module.system.api.notify.NotifyBusinessEventApi;
import cn.iocoder.yudao.module.system.api.notify.dto.NotifyBusinessEvent;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import cn.iocoder.yudao.module.system.api.user.dto.AdminUserRespDTO;
import cn.iocoder.yudao.module.zsjos.controller.admin.feedback.vo.*;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.feedback.*;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.workorder.WorkOrderHistoryDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.feedback.*;
import cn.iocoder.yudao.module.zsjos.dal.mysql.workorder.WorkOrderHistoryMapper;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import java.time.LocalDateTime;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class FeedbackApprovalServiceTest {
    @InjectMocks private FeedbackApprovalService service;
    @Mock private FeedbackMapper feedbackMapper;
    @Mock private FeedbackRoundMapper roundMapper;
    @Mock private WorkOrderHistoryMapper historyMapper;
    @Mock private BpmProcessProgressApi progressApi;
    @Mock private FeedbackDynamicFormService formService;
    @Mock private PermissionApi permissionApi;
    @Mock private AdminUserApi userApi;
    @Mock private NotifyBusinessEventApi notifyApi;
    private FeedbackDO row;
    private FeedbackRoundDO round;
    private FeedbackActionVO.UrgeReq request;
    private final BpmProcessProgressDTO.PendingTask task = new BpmProcessProgressDTO.PendingTask("task", "审核", "review", 22L, "当前审批人", LocalDateTime.now());
    @BeforeEach void setup() {
        TenantContextHolder.setTenantId(1L);
        row = new FeedbackDO(); row.setId(1L); row.setWorkOrderId(10L); row.setFeedbackType("REQUIREMENT");
        row.setStatus("APPROVING"); row.setSubmitterSubjectType("ADMIN"); row.setSubmitterUserId(11L);
        row.setVersion(3); row.setApprovalRoundNo(2); row.setProcessInstanceId("process"); row.setTitle("需求测试"); row.setFeedbackNo("XQ-test");
        round = new FeedbackRoundDO(); round.setId(12L); round.setFeedbackId(1L); round.setRoundNo(2); round.setProcessInstanceId("process");
        round.setApprovalContextJson("{\"chairmanUserId\":99}"); round.setFormSnapshotJson("[]"); round.setValueSnapshotJson("{}");
        request = new FeedbackActionVO.UrgeReq(); request.setRoundNo(2); request.setVersion(3); request.setIdempotencyKey("attempt");
        when(feedbackMapper.selectById(1L)).thenReturn(row); when(feedbackMapper.selectByIdForUpdate(1L)).thenReturn(row);
        when(roundMapper.selectByFeedbackId(1L)).thenReturn(List.of(round)); when(roundMapper.updateById(any(FeedbackRoundDO.class))).thenReturn(1);
        when(permissionApi.hasAnyPermissions(11L, FeedbackApprovalService.URGE_PERMISSION)).thenReturn(true);
        when(progressApi.getProgress("process",11L)).thenReturn(new BpmProcessProgressDTO(1,List.of(),List.of(task)));
        when(progressApi.getCurrentTasks(Set.of("process"))).thenReturn(Map.of("process",List.of(task)));
        var user = new AdminUserRespDTO(); user.setId(22L); user.setStatus(0);
        when(userApi.getUserMap(Set.of(22L))).thenReturn(Map.of(22L,user));
        when(notifyApi.publishDurable(any())).thenReturn(1);
        when(formService.parseSnapshot(any())).thenReturn(List.of()); when(formService.readDisplayValues(any(), any())).thenReturn(Map.of());
    }
    @AfterEach void cleanup() { TenantContextHolder.clear(); }
    @Test void urgesLiveAssigneeAndRecordsRoundInsteadOfFrozenApprover() {
        request.setIdempotencyKey("x".repeat(128));
        service.urge(1L,request,11L);
        var event = ArgumentCaptor.forClass(NotifyBusinessEvent.class); verify(notifyApi).publishDurable(event.capture());
        assertEquals(22L,event.getValue().getFixedRecipients().getFirst().getUserId());
        assertEquals(1L,event.getValue().getTenantId()); assertEquals("task",event.getValue().getPayload().get("taskId"));
        assertTrue(event.getValue().getSourceEventKey().length() <= 128);
        assertNotNull(round.getLastUrgedAt()); verify(historyMapper).insert(any(WorkOrderHistoryDO.class));
    }
    @Test void rejectsOtherUserEvenWithFeaturePermission() { assertError(FEEDBACK_PERMISSION_DENIED.getCode(), () -> service.urge(1L,request,22L)); verifyNoInteractions(notifyApi); }
    @Test void rejectsPartnerIdentityWithSameNumericId() { row.setSubmitterSubjectType("PARTNER_ACCOUNT"); assertError(FEEDBACK_PERMISSION_DENIED.getCode(), () -> service.urge(1L,request,11L)); }
    @Test void rejectsMissingButtonPermission() { when(permissionApi.hasAnyPermissions(11L,FeedbackApprovalService.URGE_PERMISSION)).thenReturn(false); assertError(FEEDBACK_PERMISSION_DENIED.getCode(), () -> service.urge(1L,request,11L)); }
    @Test void rejectsOldRound() { request.setRoundNo(1); assertError(FEEDBACK_APPROVAL_ROUND_INVALID.getCode(), () -> service.urge(1L,request,11L)); }
    @Test void rejectsStaleVersion() { request.setVersion(1); assertError(FEEDBACK_VERSION_CONFLICT.getCode(), () -> service.urge(1L,request,11L)); }
    @Test void rejectsCompletedProcessEvenIfBusinessEventIsDelayed() { when(progressApi.getProgress("process",11L)).thenReturn(new BpmProcessProgressDTO(2,List.of(),List.of())); assertError(FEEDBACK_URGE_NOT_RUNNING.getCode(), () -> service.urge(1L,request,11L)); }
    @Test void cooldownDoesNotPublish() { round.setLastUrgedAt(LocalDateTime.now().minusMinutes(29)); assertError(FEEDBACK_URGE_COOLDOWN.getCode(), () -> service.urge(1L,request,11L)); verifyNoInteractions(notifyApi); }
    @Test void thirtyMinutesElapsedAllowsNextUrge() { round.setLastUrgedAt(LocalDateTime.now().minusMinutes(30)); service.urge(1L,request,11L); verify(notifyApi).publishDurable(any()); }
    @Test void noActiveRecipientDoesNotConsumeCooldown() { when(progressApi.getCurrentTasks(Set.of("process"))).thenReturn(Map.of()); assertError(FEEDBACK_URGE_NO_RECIPIENT.getCode(), () -> service.urge(1L,request,11L)); assertNull(round.getLastUrgedAt()); verifyNoInteractions(notifyApi); }
    @Test void disabledUserIsNotNotified() { when(userApi.getUserMap(Set.of(22L))).thenReturn(Map.of()); assertError(FEEDBACK_URGE_NO_RECIPIENT.getCode(), () -> service.urge(1L,request,11L)); }
    @Test void missingDurableRuleDoesNotConsumeCooldown() { when(notifyApi.publishDurable(any())).thenReturn(0); assertError(FEEDBACK_URGE_NOTIFICATION_UNAVAILABLE.getCode(), () -> service.urge(1L,request,11L)); assertNull(round.getLastUrgedAt()); verify(roundMapper,never()).updateById(any(FeedbackRoundDO.class)); }
    @Test void repeatedRequestIsIdempotentEvenAfterProcessEnds() {
        var history = new WorkOrderHistoryDO();
        doAnswer(call -> { var h=call.getArgument(0,WorkOrderHistoryDO.class); history.setOperation(h.getOperation()); history.setRequestFingerprint(h.getRequestFingerprint()); return 1; }).when(historyMapper).insert(any(WorkOrderHistoryDO.class));
        service.urge(1L,request,11L); when(historyMapper.selectByOrderAndKey(10L,"attempt")).thenReturn(history); row.setStatus("WAITING");
        service.urge(1L,request,11L); verify(notifyApi,times(1)).publishDurable(any());
    }
    @Test void approverCannotReadAnotherRoundSnapshot() { assertError(FEEDBACK_APPROVAL_ROUND_INVALID.getCode(), () -> service.getApprover(1L,2,22L)); verifyNoInteractions(formService); }
    @Test void actualTransferredTaskParticipantCanReadOnlyItsRound() {
        when(progressApi.isParticipant("process",22L)).thenReturn(true);
        assertEquals(2,service.getApprover(1L,2,22L).getRoundNo());
        assertError(FEEDBACK_APPROVAL_ROUND_INVALID.getCode(), () -> service.getApprover(1L,1,22L));
    }
    @Test void adminAndOtherReadersNeverGetUrgeAction() { assertFalse(service.getAdmin(1L,2,11L).isCanUrge()); assertFalse(service.getOwn(1L,2,22L).isCanUrge()); }
    @Test void unchangedHistoricalSnapshotIsUsed() {
        round.setRoundNo(1); round.setValueSnapshotJson("{\"title\":\"原始需求\"}");
        var result=service.getOwn(1L,1,11L); assertFalse(result.isCanUrge()); verify(formService).readDisplayValues(eq(round.getValueSnapshotJson()),any());
    }
    @Test void missingProcessDiffersFromApprovalDisabled() {
        round.setProcessInstanceId(null); assertEquals("UNAVAILABLE",service.getOwn(1L,2,11L).getAvailability());
        round.setApprovalContextJson("{\"approvalEnabled\":false}"); assertEquals("NOT_REQUIRED",service.getOwn(1L,2,11L).getAvailability());
    }
    @Test void listBatchesTasksAndSurvivesBpmFailure() {
        var a=new FeedbackRespVO(); a.setFeedbackType("REQUIREMENT");a.setStatus("APPROVING");a.setProcessInstanceId("process");
        var b=new FeedbackRespVO(); b.setFeedbackType("REQUIREMENT");b.setStatus("APPROVING");b.setProcessInstanceId("process");
        service.enrich(List.of(a,b)); verify(progressApi).getCurrentTasks(Set.of("process")); verify(progressApi,never()).getProgress(any(),any());
        when(progressApi.getCurrentTasks(any())).thenThrow(new IllegalStateException("unavailable")); service.enrich(List.of(a,b)); assertEquals("UNAVAILABLE",a.getApprovalSummary().availability());
    }
    private void assertError(Integer code, Runnable action) { assertEquals(code,assertThrows(ServiceException.class,action::run).getCode()); }
}
