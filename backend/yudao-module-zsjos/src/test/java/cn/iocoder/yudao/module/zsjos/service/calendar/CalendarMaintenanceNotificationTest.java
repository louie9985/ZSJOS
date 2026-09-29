package cn.iocoder.yudao.module.zsjos.service.calendar;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import cn.iocoder.yudao.module.system.api.user.dto.AdminUserRespDTO;
import cn.iocoder.yudao.module.zsjos.controller.admin.calendar.vo.*;
import cn.iocoder.yudao.module.zsjos.controller.admin.coursecalendar.vo.CourseCalendarSaveReqVO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.calendar.*;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.coursecalendar.CourseCalendarEventDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.examcalendar.ExamScheduleDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.calendar.*;
import cn.iocoder.yudao.module.zsjos.dal.mysql.coursecalendar.CourseCalendarEventMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.examcalendar.ExamScheduleMapper;
import cn.iocoder.yudao.module.zsjos.service.coursecalendar.CourseCalendarEventService;
import cn.iocoder.yudao.module.zsjos.service.examcalendar.ExamScheduleService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.*;
import org.mockito.quality.Strictness;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants.*;

@ExtendWith(MockitoExtension.class) @MockitoSettings(strictness = Strictness.LENIENT)
class CalendarMaintenanceNotificationTest {
    @InjectMocks private CalendarMaintenanceNotificationService service;
    @Mock private CourseCalendarEventService courses;
    @Mock private ExamScheduleService exams;
    @Mock private CourseCalendarEventMapper courseMapper;
    @Mock private ExamScheduleMapper examMapper;
    @Mock private CalendarNotifyStateMapper states;
    @Mock private CalendarNotifySnapshotMapper snapshots;
    @Mock private CalendarNotifyPreviewMapper previews;
    @Mock private CalendarNotifyRecipientMapper recipients;
    @Mock private CalendarNotifyIntentMapper intentMapper;
    @Mock private CalendarNotificationIntentService intents;
    @Mock private CalendarNotificationAccess access;
    @Mock private PermissionApi permissions;
    @Mock private AdminUserApi users;
    @Mock private PlatformTransactionManager transactionManager;
    @Mock private CalendarNotificationObjectAccess objectAccess;
    private MockedStatic<SecurityFrameworkUtils> security;
    private final Map<String, CalendarNotifyPreviewDO> credentials = new HashMap<>();
    private CourseCalendarEventDO current;
    private CourseCalendarEventDO prepared;
    private CalendarNotifySnapshotDO saved;
    private CourseCalendarSaveReqVO content;

    @BeforeEach void setup() {
        TenantContextHolder.setTenantId(10L);
        security = mockStatic(SecurityFrameworkUtils.class); security.when(SecurityFrameworkUtils::getLoginUserId).thenReturn(9L);
        when(permissions.hasAnyPermissions(eq(9L), anyString())).thenReturn(true);
        when(access.check(anyString(), anyBoolean())).thenReturn(true);
        when(transactionManager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        current = course(1, "原课程"); prepared = course(2, "更新课程");
        content = new CourseCalendarSaveReqVO(); content.setCourseName("更新课程"); content.setCourseFormValue("LIVE");
        content.setStartTime(prepared.getStartTime()); content.setEndTime(prepared.getEndTime());
        when(courseMapper.selectById(7L)).thenReturn(current); when(courseMapper.selectForUpdate(7L)).thenReturn(current);
        when(courses.previewSave(nullable(Long.class), any())).thenAnswer(call -> prepared);
        when(courses.create(any())).thenReturn(7L);
        when(states.selectForUpdate(anyString(), eq(7L))).thenReturn(new CalendarNotifyStateDO().setCurrentSnapshotId(31L));
        saved = CalendarNotificationSnapshotService.projectCourse(prepared, "UPDATED", "ACTIVE").setId(31L); saved.setTenantId(10L);
        when(snapshots.selectById(31L)).thenAnswer(call -> saved);
        when(users.getUserList(anyCollection())).thenReturn(List.of(user(1L, 0), user(2L, 0)));
        when(users.getUserListByStatus(0)).thenReturn(List.of(user(1L, 0), user(2L, 0)));
        doAnswer(call -> { var p = call.<CalendarNotifyPreviewDO>getArgument(0); p.setTenantId(10L); credentials.put(p.getTokenHash(), p); return 1; })
                .when(previews).insert(any(CalendarNotifyPreviewDO.class));
        when(previews.selectToken(anyString())).thenAnswer(call -> credentials.get(call.getArgument(0)));
    }
    @AfterEach void cleanup() { security.close(); TenantContextHolder.clear(); }

    @Test void updateUsesConfirmedContentAndStagesIntentBeforeBusinessCommit() {
        var options = confirmed(previewRequest("UPDATED", 7L));
        assertEquals(7L, service.execute("COURSE", "UPDATED", 7L, content, options));
        var command = ArgumentCaptor.forClass(CalendarNotificationIntentService.Command.class);
        var order = inOrder(courseMapper, courses, intents, transactionManager);
        order.verify(courseMapper).selectForUpdate(7L); order.verify(courses).update(7L, content);
        order.verify(intents).record(command.capture()); order.verify(transactionManager).commit(any());
        assertEquals("UPDATED", command.getValue().eventType()); assertEquals(31L, command.getValue().snapshot().getId());
        assertEquals(List.of(1L, 2L), command.getValue().recipients().stream().map(r -> r.userId()).toList());
    }
    @Test void creationPreviewDoesNotNeedGeneratedIdAndReturnsExpectedVersionZero() {
        prepared.setId(null).setCalendarVersion(1);
        saved = CalendarNotificationSnapshotService.projectCourse(course(1, "更新课程"), "CREATED", "ACTIVE").setId(31L); saved.setTenantId(10L);
        var options = confirmed(previewRequest("CREATED", null)); assertEquals(0, options.getExpectedVersion());
        assertEquals(7L, service.execute("COURSE", "CREATED", null, content, options));
        verify(courses).create(content); verify(courseMapper, never()).selectForUpdate(anyLong());
        verifyNoInteractions(objectAccess);
    }
    @Test void changedContentAfterPreviewRollsBackWithoutStaging() {
        var options = confirmed(previewRequest("UPDATED", 7L)); saved.setRemarkSnapshot("changed");
        code(CALENDAR_NOTIFY_VERSION_CONFLICT.getCode(), () -> service.execute("COURSE", "UPDATED", 7L, content, options));
        verify(transactionManager).rollback(any()); verify(intents, never()).record(any());
    }
    @Test void changedVersionStopsBeforeMutation() {
        var options = confirmed(previewRequest("UPDATED", 7L)); current.setCalendarVersion(2);
        code(CALENDAR_NOTIFY_VERSION_CONFLICT.getCode(), () -> service.execute("COURSE", "UPDATED", 7L, content, options));
        verify(courses, never()).update(anyLong(), any()); verify(intents, never()).record(any());
    }
    @Test void rosterChangeAndExpiredPreviewStopBeforeMutation() {
        var req = previewRequest("UPDATED", 7L); req.setScope("ALL"); var options = confirmed(req);
        when(users.getUserListByStatus(0)).thenReturn(List.of(user(1L, 0), user(2L, 0), user(3L, 0)));
        code(CALENDAR_NOTIFY_ROSTER_CHANGED.getCode(), () -> service.execute("COURSE", "UPDATED", 7L, content, options));
        credentials.values().forEach(p -> p.setExpiresAt(LocalDateTime.now().minusMinutes(1)));
        code(CALENDAR_NOTIFY_PREVIEW_INVALID.getCode(), () -> service.execute("COURSE", "UPDATED", 7L, content, options));
        verify(courses, never()).update(anyLong(), any());
    }
    @Test void noNotifyPermissionStillMaintainsAndRecordsEmptyIntentForSkippedOutcome() {
        var options = options(); options.setPreviewToken(null);
        when(access.check("COURSE", false)).thenThrow(new ServiceException(CALENDAR_NOTIFY_PERMISSION_DENIED));
        service.execute("COURSE", "UPDATED", 7L, content, options);
        var command = ArgumentCaptor.forClass(CalendarNotificationIntentService.Command.class); verify(intents).record(command.capture());
        assertTrue(command.getValue().requested()); assertTrue(command.getValue().recipients().isEmpty());
        verify(courses).update(7L, content); verifyNoInteractions(previews, users);
    }
    @Test void explicitCancellationNeedsNoPreviewOrSendPermission() {
        var options = options(); options.setSend(false);
        service.execute("COURSE", "UPDATED", 7L, content, options);
        var command = ArgumentCaptor.forClass(CalendarNotificationIntentService.Command.class); verify(intents).record(command.capture());
        assertFalse(command.getValue().requested()); verifyNoInteractions(access, previews, users);
    }
    @Test void originalRecipientUnionKeepsLatestHistoricalNameAndMarksUnavailable() {
        when(recipients.selectHistoricalRecipients(10L, "COURSE", 7L)).thenReturn(List.of(
                new CalendarNotifyRecipientDO().setUserId(1L).setNicknameSnapshot("最近快照"),
                new CalendarNotifyRecipientDO().setUserId(2L).setNicknameSnapshot("停用员工"),
                new CalendarNotifyRecipientDO().setUserId(1L).setNicknameSnapshot("更早快照")));
        when(users.getUserList(anyCollection())).thenReturn(List.of(user(1L, 0)));
        var req = previewRequest("DELETED", 7L); req.setOriginalRecipients(true); req.setUserIds(null);
        var preview = service.preview(req); assertEquals(2, preview.recipientCount()); assertEquals(1, preview.invalidRecipientCount());
        var options = options(); options.setOriginalRecipients(true); options.setUserIds(null); options.setPreviewToken(preview.previewToken());
        var deleted = course(2, "原课程");
        saved = CalendarNotificationSnapshotService.projectCourse(deleted, "DELETED", "DELETED").setId(31L); saved.setTenantId(10L);
        service.execute("COURSE", "DELETED", 7L, null, options);
        var command = ArgumentCaptor.forClass(CalendarNotificationIntentService.Command.class); verify(intents).record(command.capture());
        assertEquals("最近快照", command.getValue().recipients().get(0).nicknameSnapshot());
        assertEquals("EMPLOYEE_UNAVAILABLE", command.getValue().recipients().get(1).skipReason());
        verify(courses).delete(7L);
    }
    @Test void identicalCommittedOperationReturnsOriginalResultWithoutMutatingAgain() {
        var previous = new CalendarNotifyIntentDO().setCalendarId(7L);
        when(intents.findOperation(eq("op"), anyString(), eq(9L))).thenReturn(previous);
        assertEquals(7L, service.execute("COURSE", "CREATED", null, content, options()));
        verifyNoInteractions(courses, transactionManager, objectAccess);
    }
    @Test void concurrentDeleteReplaySurvivesObjectDisappearingBeforeLock() {
        when(intents.findOperation(eq("op"), anyString(), eq(9L)))
                .thenReturn(null, new CalendarNotifyIntentDO().setCalendarId(7L));
        doThrow(new ServiceException(COURSE_CALENDAR_NOT_EXISTS)).when(objectAccess).courseMaintenance(7L);
        assertEquals(7L, service.execute("COURSE", "DELETED", 7L, null, options()));
        verifyNoInteractions(courses, transactionManager);
        verify(intents, times(2)).findOperation(eq("op"), anyString(), eq(9L));
    }

    @Test void concurrentDuplicateIsReReadOnlyAfterLoserTransactionRollsBack() {
        var options = confirmed(previewRequest("UPDATED", 7L));
        when(intents.findOperation(eq("op"), anyString(), eq(9L))).thenReturn(null, null, new CalendarNotifyIntentDO().setCalendarId(7L));
        doThrow(new org.springframework.dao.DuplicateKeyException("intent key")).when(intents).record(any());
        assertEquals(7L, service.execute("COURSE", "UPDATED", 7L, content, options));
        var order = inOrder(intents, transactionManager);
        order.verify(intents, calls(2)).findOperation(eq("op"), anyString(), eq(9L));
        order.verify(intents).record(any()); order.verify(transactionManager).rollback(any());
        order.verify(intents).findOperation(eq("op"), anyString(), eq(9L));
    }
    @Test void operationLookupIsOwnerAndTenantScopedWithoutRequiringNotificationPermission() {
        var row = new CalendarNotifyIntentDO().setCalendarId(7L).setCalendarType("COURSE").setOperatorUserId(9L).setStatus("SKIPPED");
        row.setTenantId(10L); when(intentMapper.selectOperation("op")).thenReturn(row);
        assertEquals("SKIPPED", service.getResult("op").notificationStatus()); verifyNoInteractions(access);
        row.setTenantId(11L); code(CALENDAR_NOTIFY_OPERATION_NOT_EXISTS.getCode(), () -> service.getResult("op"));
    }
    @Test void objectDenialStopsMaintenanceBeforeTransactionAndRosterRead() {
        doThrow(new ServiceException(COURSE_CALENDAR_NOT_EXISTS)).when(objectAccess).courseMaintenance(7L);
        code(COURSE_CALENDAR_NOT_EXISTS.getCode(), () -> service.execute("COURSE", "UPDATED", 7L, content, options()));
        verifyNoInteractions(transactionManager, courses, users, previews);
        verify(intents, never()).record(any());
    }

    @Test void managePermissionIsRequiredEvenWhenSendPermissionExists() {
        when(permissions.hasAnyPermissions(9L, "zsjos:course-calendar:manage")).thenReturn(false);
        code(CALENDAR_NOTIFY_MANAGE_PERMISSION_DENIED.getCode(), () -> service.preview(previewRequest("UPDATED", 7L)));
        verifyNoInteractions(courses, users, access);
    }
    @Test void examPublishUsesProjectedFrozenContentAndExistingDomainCommand() {
        var before = new ExamScheduleDO().setId(7L).setCalendarVersion(1).setRecordStatus("DRAFT");
        var after = new ExamScheduleDO().setId(7L).setCalendarVersion(2).setRecordStatus("PUBLISHED")
                .setScheduleType("EXACT").setExactDate(LocalDate.of(2027, 1, 1)).setCategoryNameSnapshot("考期");
        when(examMapper.selectForUpdate(7L)).thenReturn(before); when(exams.previewTransition(7L, "PUBLISHED", 9L)).thenReturn(after);
        saved = CalendarNotificationSnapshotService.projectExam(after, "PUBLISHED").setId(31L); saved.setTenantId(10L);
        var req = previewRequest("PUBLISHED", 7L); req.setCalendarType("EXAM"); req.setCourseContent(null);
        var options = confirmed(req);
        service.execute("EXAM", "PUBLISHED", 7L, null, options);
        verify(exams).publish(7L, 9L); verify(intents).record(any());
    }

    private CalendarNotifyReqVO previewRequest(String event, Long id) {
        var req = new CalendarNotifyReqVO(); req.setCalendarType("COURSE"); req.setCalendarId(id); req.setMaintenanceAction(event);
        req.setScope("SPECIFIED"); req.setUserIds(List.of(1L, 2L)); req.setCourseContent("DELETED".equals(event) ? null : content); return req;
    }
    private CalendarMaintenanceNotifyReqVO options() {
        var options = new CalendarMaintenanceNotifyReqVO(); options.setOperationKey("op"); options.setExpectedVersion(1);
        options.setUserIds(List.of(1L, 2L)); return options;
    }
    private CalendarMaintenanceNotifyReqVO confirmed(CalendarNotifyReqVO req) {
        var preview = service.preview(req); var options = options(); options.setExpectedVersion(preview.expectedVersion());
        options.setScope(req.getScope()); options.setOriginalRecipients(Boolean.TRUE.equals(req.getOriginalRecipients()));
        options.setUserIds(req.getUserIds()); options.setPreviewToken(preview.previewToken()); return options;
    }
    private CourseCalendarEventDO course(int version, String name) {
        return new CourseCalendarEventDO().setId(7L).setCalendarVersion(version).setCourseName(name).setCourseFormValue("LIVE")
                .setCourseFormLabelSnapshot("直播").setStartTime(LocalDateTime.of(2027, 1, 1, 9, 0)).setEndTime(LocalDateTime.of(2027, 1, 1, 10, 0));
    }
    private AdminUserRespDTO user(Long id, int status) { var row = new AdminUserRespDTO(); row.setId(id); row.setStatus(status); row.setNickname("当前名字"); return row; }
    private void code(int expected, Runnable call) { assertEquals(expected, assertThrows(ServiceException.class, call::run).getCode()); }
}
