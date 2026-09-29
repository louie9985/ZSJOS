package cn.iocoder.yudao.module.zsjos.service.calendar;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import cn.iocoder.yudao.module.system.api.user.dto.AdminUserRespDTO;
import cn.iocoder.yudao.module.zsjos.controller.admin.calendar.vo.CalendarNotifySendRespVO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.calendar.*;
import cn.iocoder.yudao.module.zsjos.dal.mysql.calendar.*;
import cn.iocoder.yudao.module.zsjos.dal.mysql.coursecalendar.CourseCalendarEventMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.examcalendar.ExamScheduleMapper;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.TransientDataAccessResourceException;
import java.time.LocalDateTime;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CalendarNotificationIntentTest {
    @InjectMocks private CalendarNotificationIntentService intents;
    @InjectMocks private CalendarNotificationIntentRecoveryService recovery;
    @Mock private CalendarNotifyIntentMapper mapper;
    @Mock private CalendarNotifyStateMapper stateMapper;
    @Mock private CalendarNotifySnapshotMapper snapshotMapper;
    @Mock private CourseCalendarEventMapper courseMapper;
    @Mock private ExamScheduleMapper examMapper;
    @Mock private AdminUserApi users;
    @Mock private CalendarNotificationAccess access;
    @Mock private CalendarNotificationAcceptanceService acceptance;
    @Mock private ApplicationEventPublisher events;
    private CalendarNotifySnapshotDO snapshot;
    private CalendarNotifyIntentDO row;
    private CalendarNotifyStateDO state;
    private final List<CalendarNotificationAcceptanceService.Recipient> roster = List.of(
            new CalendarNotificationAcceptanceService.Recipient(1L, "原姓名一", null),
            new CalendarNotificationAcceptanceService.Recipient(2L, "原姓名二", null));

    @BeforeEach void setup() {
        TenantContextHolder.setTenantId(10L);
        snapshot = new CalendarNotifySnapshotDO().setId(31L).setCalendarId(7L).setCalendarType("COURSE")
                .setCalendarVersion(2).setRecordStatus("ACTIVE"); snapshot.setTenantId(10L);
        row = new CalendarNotifyIntentDO().setId(5L).setCalendarId(7L).setCalendarType("COURSE").setSnapshotId(31L)
                .setScope("ALL").setEventType("UPDATED").setOperatorUserId(9L).setRecipientsJson(JsonUtils.toJsonString(roster))
                .setAcceptanceKey("fixed-key").setRequestHash("a".repeat(64)).setOperationKey("operation")
                .setResend(false).setAttemptCount(0).setStatus("PENDING").setNextAttemptAt(LocalDateTime.now().minusSeconds(1));
        row.setTenantId(10L);
        state = new CalendarNotifyStateDO().setCurrentSnapshotId(31L); state.setTenantId(10L);
        when(mapper.selectById(5L)).thenReturn(row); when(mapper.selectForUpdate(5L)).thenReturn(row);
        when(stateMapper.selectForUpdate("COURSE", 7L)).thenReturn(state);
        when(snapshotMapper.selectById(31L)).thenReturn(snapshot);
        when(users.getUser(9L)).thenReturn(user(9L, 0));
        when(users.getUserList(anyCollection())).thenReturn(List.of(user(1L, 0), user(2L, 0)));
        when(acceptance.accept(any())).thenReturn(new CalendarNotifySendRespVO(40L, 2, false, "event", 0, "SUBMITTED"));
        doAnswer(call -> { call.<CalendarNotifyIntentDO>getArgument(0).setId(5L); return 1; }).when(mapper).insert(any(CalendarNotifyIntentDO.class));
    }
    @AfterEach void cleanup() { TenantContextHolder.clear(); }

    @Test void recordsImmutableRosterAndSignalsOnlyAfterPersistence() {
        var saved = intents.record(command());
        assertEquals("PENDING", saved.getStatus()); assertEquals(roster, JsonUtils.parseArray(saved.getRecipientsJson(), CalendarNotificationAcceptanceService.Recipient.class));
        assertTrue(saved.getAcceptanceKey().startsWith("calendar-intent:"));
        var order = inOrder(mapper, events);
        order.verify(mapper).selectOperation("operation");
        order.verify(mapper).insert(saved);
        order.verify(events).publishEvent(new CalendarNotificationIntentService.Ready(10L, 5L));
    }
    @Test void missingNotificationPermissionRecordsSkippedInsteadOfFailingMaintenance() {
        when(access.checkForUser("COURSE", true, 9L)).thenThrow(new ServiceException(CALENDAR_NOTIFY_ALL_PERMISSION_DENIED));
        var saved = intents.record(command());
        assertEquals("SKIPPED", saved.getStatus()); assertEquals("1900092003", saved.getLastErrorCode());
        assertNotNull(saved.getCompletedTime()); verifyNoInteractions(events);
    }
    @Test void cancellationIsPersistedWithoutCheckingSendPermissionOrSchedulingRecovery() {
        var original = command();
        var saved = intents.record(new CalendarNotificationIntentService.Command(original.operationKey(),
                original.requestHash(), original.snapshot(), original.eventType(), original.scope(),
                List.of(), original.resend(), original.operatorUserId(), false));
        assertEquals("SKIPPED", saved.getStatus());
        assertEquals("CANCELLED", saved.getLastErrorCode());
        assertNotNull(saved.getCompletedTime());
        verify(mapper).insert(saved);
        verifyNoInteractions(access, events);
    }

    @Test void operationReplayRetainsOriginalIntentAndRejectsDifferentActor() {
        when(mapper.selectOperation("operation")).thenReturn(row);
        assertSame(row, intents.record(command())); verify(mapper, never()).insert(any(CalendarNotifyIntentDO.class));
        assertEquals(CALENDAR_NOTIFY_IDEMPOTENCY_CONFLICT.getCode(), assertThrows(ServiceException.class,
                () -> intents.findOperation("operation", "a".repeat(64), 11L)).getCode());
        verifyNoInteractions(events);
    }
    @Test void recoveryUsesOnlyFixedRosterAndKeepsHistoricalNames() {
        when(users.getUserList(anyCollection())).thenReturn(List.of(user(1L, 0), user(2L, 1), user(3L, 0)));
        recovery.recover(5L);
        var command = ArgumentCaptor.forClass(CalendarNotificationAcceptanceService.Command.class);
        verify(acceptance).accept(command.capture());
        assertEquals(List.of(1L, 2L), command.getValue().recipients().stream().map(r -> r.userId()).toList());
        assertEquals("原姓名一", command.getValue().recipients().get(0).nicknameSnapshot());
        assertEquals("EMPLOYEE_UNAVAILABLE", command.getValue().recipients().get(1).skipReason());
        assertEquals("fixed-key", command.getValue().idempotencyKey());
        verify(users, never()).getUserListByStatus(anyInt());
        var order = inOrder(courseMapper, stateMapper, mapper, access, acceptance);
        order.verify(courseMapper).selectForUpdate(7L); order.verify(stateMapper).selectForUpdate("COURSE", 7L);
        order.verify(mapper).selectForUpdate(5L); order.verify(access).checkForUser("COURSE", true, 9L);
        order.verify(acceptance).accept(any());
        verify(mapper).updatePending(eq(5L), eq(10L), eq("SUBMITTED"), eq(40L), eq(1), any(), isNull(), any());
    }
    @Test void supersededContentDoesNotPublishOldArrangement() {
        state.setCurrentSnapshotId(32L); recovery.recover(5L);
        verify(mapper).updatePending(eq(5L), eq(10L), eq("SUPERSEDED"), isNull(), eq(1), any(), eq("CONTENT_CHANGED"), any());
        verifyNoInteractions(acceptance, users);
    }
    @Test void revokedPermissionIsRecheckedWithoutRollingBackBusiness() {
        when(access.checkForUser("COURSE", true, 9L)).thenThrow(new ServiceException(CALENDAR_NOTIFY_PERMISSION_DENIED));
        recovery.recover(5L);
        verify(mapper).updatePending(eq(5L), eq(10L), eq("SKIPPED"), isNull(), eq(1), any(), eq("1900092002"), any());
        verifyNoInteractions(acceptance);
    }
    @Test void inactiveOperatorNeverTriggersSystemAcceptance() {
        when(users.getUser(9L)).thenReturn(user(9L, 1)); recovery.recover(5L);
        verify(mapper).updatePending(eq(5L), eq(10L), eq("SKIPPED"), isNull(), eq(1), any(), eq("OPERATOR_INACTIVE"), any());
        verifyNoInteractions(access, acceptance);
    }
    @Test void committedAndForeignIntentsAreNotProcessed() {
        row.setStatus("SUBMITTED"); recovery.recover(5L);
        row.setStatus("PENDING"); row.setTenantId(11L); recovery.recover(5L);
        verifyNoInteractions(courseMapper, stateMapper, access, acceptance);
    }
    @Test void configurationFailureRemainsRecoverableAndDoesNotPersistSensitiveErrorText() {
        recovery.recordFailure(5L, new ServiceException(CALENDAR_NOTIFY_CONFIG_UNAVAILABLE));
        verify(mapper).updatePending(eq(5L), eq(10L), eq("PENDING"), isNull(), eq(1), any(), eq("1900092015"), isNull());
    }
    @Test void transientFailureRetriesButProgrammingFailureDoesNotLoop() {
        recovery.recordFailure(5L, new TransientDataAccessResourceException("sensitive diagnostic"));
        verify(mapper).updatePending(eq(5L), eq(10L), eq("PENDING"), isNull(), eq(1), any(), eq("DATABASE_RETRY"), isNull());
        recovery.recordFailure(5L, new IllegalStateException("sensitive diagnostic"));
        verify(mapper).updatePending(eq(5L), eq(10L), eq("FAILED"), isNull(), eq(1), any(), eq("ACCEPTANCE_FAILED"), any());
    }
    @Test void failureRecorderCannotOverwriteAnotherWorkersSuccess() {
        row.setStatus("SUBMITTED"); recovery.recordFailure(5L, new IllegalStateException());
        verify(mapper, never()).updatePending(any(), any(), any(), any(), anyInt(), any(), any(), any());
    }
    private CalendarNotificationIntentService.Command command() {
        return new CalendarNotificationIntentService.Command("operation", "a".repeat(64), snapshot, "UPDATED", "ALL", roster, false, 9L);
    }
    private AdminUserRespDTO user(Long id, int status) {
        var user = new AdminUserRespDTO(); user.setId(id); user.setStatus(status); user.setNickname("当前名字"); return user;
    }
}
