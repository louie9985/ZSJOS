package cn.iocoder.yudao.module.zsjos.service.calendar;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.system.api.notify.NotifyBusinessEventApi;
import cn.iocoder.yudao.module.system.api.notify.dto.NotifyBusinessEvent;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import cn.iocoder.yudao.module.system.api.user.dto.AdminUserRespDTO;
import cn.iocoder.yudao.module.zsjos.controller.admin.calendar.vo.CalendarNotifyReqVO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.calendar.*;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.coursecalendar.CourseCalendarEventDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.calendar.*;
import cn.iocoder.yudao.module.zsjos.dal.mysql.coursecalendar.CourseCalendarEventMapper;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.*;
import org.mockito.quality.Strictness;
import java.time.LocalDateTime;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CalendarNotificationSendTest {
    @InjectMocks private CalendarNotificationService service;
    private CalendarNotificationAcceptanceService acceptance;
    @Mock private AdminUserApi adminUserApi;
    @Mock private NotifyBusinessEventApi notifyApi;
    @Mock private CalendarNotifyBatchMapper batchMapper;
    @Mock private CalendarNotifyRecipientMapper recipientMapper;
    @Mock private CalendarNotifyStateMapper stateMapper;
    @Mock private CourseCalendarEventMapper courseMapper;
    @Mock private CalendarNotificationAccess notificationAccess;
    @Mock private CalendarNotificationObjectAccess objectAccess;
    @Mock private CalendarNotificationSnapshotService snapshots;
    @Mock private CalendarNotifyPreviewMapper previewMapper;
    private MockedStatic<SecurityFrameworkUtils> security;
    private final Map<String, CalendarNotifyBatchDO> batches = new HashMap<>();
    private final Map<String, CalendarNotifyPreviewDO> previews = new HashMap<>();
    private final Set<Long> accepted = new HashSet<>();
    private List<AdminUserRespDTO> employees;
    private CourseCalendarEventDO course;

    @BeforeEach void setup() {
        TenantContextHolder.setTenantId(10L);
        acceptance = new CalendarNotificationAcceptanceService();
        org.springframework.test.util.ReflectionTestUtils.setField(acceptance, "batchMapper", batchMapper);
        org.springframework.test.util.ReflectionTestUtils.setField(acceptance, "recipientMapper", recipientMapper);
        org.springframework.test.util.ReflectionTestUtils.setField(acceptance, "stateMapper", stateMapper);
        org.springframework.test.util.ReflectionTestUtils.setField(acceptance, "notifyApi", notifyApi);
        org.springframework.test.util.ReflectionTestUtils.setField(service, "acceptance", acceptance);
        security = mockStatic(SecurityFrameworkUtils.class);
        security.when(SecurityFrameworkUtils::getLoginUserId).thenReturn(9L);
        employees = new ArrayList<>(List.of(user(1L), user(2L)));
        course = new CourseCalendarEventDO().setId(7L).setCalendarVersion(1).setCourseName("课程")
                .setCourseFormValue("LIVE").setCourseFormLabelSnapshot("直播")
                .setStartTime(LocalDateTime.of(2026, 10, 1, 9, 0)).setEndTime(LocalDateTime.of(2026, 10, 1, 10, 0));
        when(courseMapper.selectById(7L)).thenReturn(course);
        when(courseMapper.selectForUpdate(7L)).thenReturn(course);
        when(snapshots.captureCourse(any(), anyString(), anyString())).thenAnswer(call ->
                {
                    var snapshot = CalendarNotificationSnapshotService.projectCourse(course, "MANUAL", "ACTIVE").setId(31L);
                    snapshot.setTenantId(10L); return snapshot;
                });
        when(adminUserApi.getUserList(anyCollection())).thenAnswer(call -> employees.stream()
                .filter(user -> call.<Collection<Long>>getArgument(0).contains(user.getId())).toList());
        when(adminUserApi.getUserListByStatus(0)).thenAnswer(call -> List.copyOf(employees));
        when(recipientMapper.selectAcceptedUserIds(10L, "COURSE", 7L, 1)).thenAnswer(call -> new ArrayList<>(accepted));
        doAnswer(call -> {
            var preview = call.<CalendarNotifyPreviewDO>getArgument(0); preview.setTenantId(10L);
            previews.put(preview.getTokenHash(), preview); return 1;
        }).when(previewMapper).insert(any(CalendarNotifyPreviewDO.class));
        when(previewMapper.selectToken(anyString())).thenAnswer(call -> previews.get(call.getArgument(0)));
        when(batchMapper.selectIdempotencyKey(anyString())).thenAnswer(call -> batches.get(call.getArgument(0)));
        doAnswer(call -> {
            var batch = call.<CalendarNotifyBatchDO>getArgument(0); batch.setId((long) batches.size() + 1);
            batches.put(batch.getIdempotencyKey(), batch); return 1;
        }).when(batchMapper).insert(any(CalendarNotifyBatchDO.class));
        doAnswer(call -> {
            var recipient = call.<CalendarNotifyRecipientDO>getArgument(0);
            if (Boolean.TRUE.equals(recipient.getAccepted())) accepted.add(recipient.getUserId());
            return 1;
        }).when(recipientMapper).insert(any(CalendarNotifyRecipientDO.class));
        when(notifyApi.publishDurable(any())).thenReturn(2);
        var state = new CalendarNotifyStateDO().setCurrentSnapshotId(31L).setCalendarVersion(1).setRecordStatus("ACTIVE");
        state.setTenantId(10L);
        when(stateMapper.selectForUpdate("COURSE", 7L)).thenReturn(state);
    }
    @AfterEach void cleanup() { security.close(); TenantContextHolder.clear(); }

    @Test void previewCountsIntersectionOfSelectedPeopleAndAcceptedPeople() {
        accepted.addAll(List.of(1L, 3L));
        var preview = service.preview(request());
        assertEquals(2, preview.recipientCount()); assertEquals(1, preview.notifiedCount());
        assertEquals(1, preview.newRecipientCount()); assertNotNull(preview.previewToken());
        verify(courseMapper, never()).selectForUpdate(anyLong());
    }
    @Test void objectDenialStopsBeforeReadingRecipientsOrPublishing() {
        doThrow(new ServiceException(COURSE_CALENDAR_NOT_EXISTS)).when(objectAccess).courseNotification(7L);
        assertCode(COURSE_CALENDAR_NOT_EXISTS.getCode(), () -> service.preview(request()));
        verifyNoInteractions(adminUserApi, previewMapper, notifyApi);
    }

    @Test void sameKeySameRequestReturnsOriginalAndDifferentContentConflicts() {
        var req = confirmed(request());
        var first = service.send(req);
        assertEquals(first, service.send(req));
        verify(notifyApi, times(1)).publishDurable(any());
        req.setUserIds(List.of(1L));
        assertCode(CALENDAR_NOTIFY_IDEMPOTENCY_CONFLICT.getCode(), () -> service.send(req));
    }
    @Test void committedReplaySurvivesDeletionAndExpiredPreviewWithoutAnotherDelivery() {
        var req = confirmed(request());
        var first = service.send(req);
        when(courseMapper.selectForUpdate(7L)).thenReturn(null);
        clearInvocations(objectAccess);
        doThrow(new ServiceException(COURSE_CALENDAR_NOT_EXISTS)).when(objectAccess).courseNotification(7L);
        previews.clear();
        assertEquals(first, service.send(req));
        verifyNoInteractions(objectAccess);
        verify(courseMapper, times(1)).selectForUpdate(7L);
        verify(notifyApi, times(1)).publishDurable(any());
    }
    @Test void committedReplayStillChecksCurrentPermission() {
        var req = confirmed(request());
        service.send(req);
        when(notificationAccess.check("COURSE", false)).thenThrow(new ServiceException(CALENDAR_NOTIFY_PERMISSION_DENIED));
        assertCode(CALENDAR_NOTIFY_PERMISSION_DENIED.getCode(), () -> service.send(req));
        verify(notifyApi, times(1)).publishDurable(any());
    }
    @Test void replayCannotCrossOperatorOrTenantEvenWhenMapperReturnsForeignRow() {
        var req = confirmed(request());
        service.send(req);
        security.when(SecurityFrameworkUtils::getLoginUserId).thenReturn(11L);
        assertCode(CALENDAR_NOTIFY_IDEMPOTENCY_CONFLICT.getCode(), () -> service.send(req));
        security.when(SecurityFrameworkUtils::getLoginUserId).thenReturn(9L);
        TenantContextHolder.setTenantId(11L);
        assertCode(CALENDAR_NOTIFY_IDEMPOTENCY_CONFLICT.getCode(), () -> service.send(req));
        verify(notifyApi, times(1)).publishDurable(any());
    }
    @Test void lifecycleAcceptanceUsesFixedRosterAndRetainsSkippedHistoricalPeople() {
        stateMapper.selectForUpdate("COURSE", 7L).setRecordStatus("DELETED");
        var snapshot = CalendarNotificationSnapshotService.projectCourse(course, "DELETED", "DELETED").setId(31L);
        snapshot.setTenantId(10L);
        var result = acceptance.accept(new CalendarNotificationAcceptanceService.Command(snapshot, "DELETED", "SPECIFIED",
                List.of(new CalendarNotificationAcceptanceService.Recipient(1L, "原员工", "EMPLOYEE_INACTIVE"),
                        new CalendarNotificationAcceptanceService.Recipient(2L, "接收员工", null)), false, 9L, "delete", "delete-hash"));
        assertEquals(1, result.acceptedCount()); assertEquals(1, result.skippedCount());
        var records = ArgumentCaptor.forClass(CalendarNotifyRecipientDO.class);
        verify(recipientMapper, times(2)).insert(records.capture());
        assertEquals("EMPLOYEE_INACTIVE", records.getAllValues().get(0).getSkipReason());
        assertNotNull(records.getAllValues().get(0).getCompletedTime());
        assertNull(records.getAllValues().get(0).getDedupKey());
        var event = ArgumentCaptor.forClass(NotifyBusinessEvent.class);
        verify(notifyApi).publishDurable(event.capture());
        assertEquals("DELETED", event.getValue().getPayload().get("calendar.eventType"));
        assertEquals(List.of(2L), event.getValue().getFixedRecipients().stream().map(x -> x.getUserId()).toList());
        assertEquals(2, event.getValue().getFixedRecipients().get(0).getUserType());
    }
    @Test void lifecycleAllSkippedDoesNotPublishEmptyFixedEvent() {
        stateMapper.selectForUpdate("COURSE", 7L).setRecordStatus("DELETED");
        var snapshot = CalendarNotificationSnapshotService.projectCourse(course, "DELETED", "DELETED").setId(31L);
        snapshot.setTenantId(10L);
        var result = acceptance.accept(new CalendarNotificationAcceptanceService.Command(snapshot, "DELETED", "SPECIFIED",
                List.of(new CalendarNotificationAcceptanceService.Recipient(1L, "原员工", "EMPLOYEE_INACTIVE")),
                false, 9L, "delete", "delete-hash"));
        assertEquals("SKIPPED", result.status()); assertEquals(0, result.acceptedCount());
        verifyNoInteractions(notifyApi);
    }
    @Test void lifecycleAcceptanceRejectsSupersededSnapshotBeforeWritingAnyBatch() {
        var snapshot = CalendarNotificationSnapshotService.projectCourse(course, "UPDATED", "ACTIVE").setId(30L);
        snapshot.setTenantId(10L);
        var command = new CalendarNotificationAcceptanceService.Command(snapshot, "UPDATED", "SPECIFIED",
                List.of(new CalendarNotificationAcceptanceService.Recipient(1L, "员工", null)), false, 9L, "update", "hash");
        assertCode(CALENDAR_NOTIFY_VERSION_CONFLICT.getCode(), () -> acceptance.accept(command));
        verify(batchMapper, never()).insert(any(CalendarNotifyBatchDO.class));
        verifyNoInteractions(notifyApi);
    }
    @Test void lifecycleAcceptanceCannotLabelDeletedSnapshotAsNormalReminder() {
        stateMapper.selectForUpdate("COURSE", 7L).setRecordStatus("DELETED");
        var snapshot = CalendarNotificationSnapshotService.projectCourse(course, "DELETED", "DELETED").setId(31L);
        snapshot.setTenantId(10L);
        var command = new CalendarNotificationAcceptanceService.Command(snapshot, "MANUAL", "SPECIFIED",
                List.of(new CalendarNotificationAcceptanceService.Recipient(1L, "员工", null)), true, 9L, "reminder", "hash");
        assertCode(CALENDAR_NOTIFY_STATE_INVALID.getCode(), () -> acceptance.accept(command));
        verify(batchMapper, never()).insert(any(CalendarNotifyBatchDO.class));
    }
    @Test void requestCommittedWhileWaitingOnLockIsReplayed() {
        var req = confirmed(request());
        var first = service.send(req);
        var committed = batches.get("first");
        when(batchMapper.selectIdempotencyKey("first")).thenReturn(null, committed);
        assertEquals(first, service.send(req));
        verify(notifyApi, times(1)).publishDurable(any());
    }
    @Test void newKeyDoesNotBypassDefaultDedupButNewPeopleCanBeAdded() {
        service.send(confirmed(request()));
        var second = request(); second.setIdempotencyKey("second");
        var skipped = service.send(confirmed(second));
        assertEquals(0, skipped.acceptedCount()); assertEquals(2, skipped.skippedCount());
        assertEquals("SKIPPED", skipped.status());
        employees.add(user(3L));
        second.setIdempotencyKey("third"); second.setUserIds(List.of(1L, 2L, 3L));
        assertEquals(1, service.send(confirmed(second)).acceptedCount());
        var events = ArgumentCaptor.forClass(NotifyBusinessEvent.class);
        verify(notifyApi, times(2)).publishDurable(events.capture());
        assertEquals(List.of(3L), events.getAllValues().get(1).getFixedRecipients().stream().map(x -> x.getUserId()).toList());
    }
    @Test void explicitReminderCreatesIndependentBatchWithoutNormalUniqueKey() {
        service.send(confirmed(request()));
        var reminder = request(); reminder.setResend(true); reminder.setIdempotencyKey("reminder");
        var result = service.send(confirmed(reminder));
        assertEquals(2, result.acceptedCount());
        var recipients = ArgumentCaptor.forClass(CalendarNotifyRecipientDO.class);
        verify(recipientMapper, times(4)).insert(recipients.capture());
        assertNotNull(recipients.getAllValues().get(0).getDedupKey());
        assertNull(recipients.getAllValues().get(2).getDedupKey());
    }
    @Test void changedVersionFailsBeforeBatchCreation() {
        var req = confirmed(request()); course.setCalendarVersion(2);
        assertCode(CALENDAR_NOTIFY_VERSION_CONFLICT.getCode(), () -> service.send(req));
        verify(batchMapper, never()).insert(any(CalendarNotifyBatchDO.class));
    }
    @Test void sameVersionContentMutationAlsoInvalidatesPreview() {
        var req = confirmed(request()); course.setRemark("变更");
        assertCode(CALENDAR_NOTIFY_VERSION_CONFLICT.getCode(), () -> service.send(req));
    }
    @Test void changedAllRosterRequiresNewConfirmation() {
        var req = request(); req.setScope("ALL"); confirmed(req); employees.add(user(3L));
        assertCode(CALENDAR_NOTIFY_ROSTER_CHANGED.getCode(), () -> service.send(req));
    }
    @Test void expiredPreviewAndChangedInputCannotBeReused() {
        var req = confirmed(request()); previews.values().forEach(p -> p.setExpiresAt(LocalDateTime.now().minusMinutes(1)));
        assertCode(CALENDAR_NOTIFY_PREVIEW_INVALID.getCode(), () -> service.send(req));
        confirmed(req); req.setResend(true);
        assertCode(CALENDAR_NOTIFY_PREVIEW_INVALID.getCode(), () -> service.send(req));
    }
    @Test void invalidOrMissingEmployeeDoesNotRevealForeignIdentity() {
        employees.remove(1);
        assertCode(CALENDAR_NOTIFY_RECIPIENT_INVALID.getCode(), () -> service.preview(request()));
        employees.add(user(2L)); employees.get(1).setStatus(1);
        assertCode(CALENDAR_NOTIFY_RECIPIENT_INVALID.getCode(), () -> service.preview(request()));
    }
    @Test void missingRulesFailAcceptanceAndClientEventTypeIsIgnored() {
        var req = confirmed(request()); req.setEventType("DELETED");
        when(notifyApi.publishDurable(any())).thenReturn(0);
        assertCode(CALENDAR_NOTIFY_CONFIG_UNAVAILABLE.getCode(), () -> service.send(req));
        var event = ArgumentCaptor.forClass(NotifyBusinessEvent.class); verify(notifyApi).publishDurable(event.capture());
        assertEquals("MANUAL", event.getValue().getPayload().get("calendar.eventType"));
    }

    private CalendarNotifyReqVO request() {
        var req = new CalendarNotifyReqVO(); req.setCalendarType("COURSE"); req.setCalendarId(7L);
        req.setScope("SPECIFIED"); req.setUserIds(List.of(1L, 2L)); req.setIdempotencyKey("first"); return req;
    }
    private CalendarNotifyReqVO confirmed(CalendarNotifyReqVO req) {
        var preview = service.preview(req); req.setCalendarVersion(preview.calendarVersion()); req.setPreviewToken(preview.previewToken()); return req;
    }
    private AdminUserRespDTO user(Long id) { var user = new AdminUserRespDTO(); user.setId(id); user.setNickname("测试员工"); user.setStatus(0); return user; }
    private void assertCode(int code, Runnable action) { assertEquals(code, assertThrows(ServiceException.class, action::run).getCode()); }
}
