package cn.iocoder.yudao.module.zsjos.service.calendar;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.zsjos.controller.admin.product.vo.ProductSpecVO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.calendar.*;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.coursecalendar.CourseCalendarEventDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.examcalendar.ExamScheduleDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.calendar.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants.CALENDAR_NOTIFY_SNAPSHOT_CONFLICT;

@ExtendWith(MockitoExtension.class)
class CalendarNotificationSnapshotTest {
    @InjectMocks private CalendarNotificationSnapshotService service;
    @Mock private CalendarNotifySnapshotMapper snapshotMapper;
    @Mock private CalendarNotifyStateMapper stateMapper;
    @BeforeEach void setup() { TenantContextHolder.setTenantId(10L); }
    @AfterEach void cleanup() { TenantContextHolder.clear(); }

    @Test void capturesCourseFormAndCancellationWithoutDestroyingEarlierSnapshot() {
        doAnswer(call -> { call.<CalendarNotifySnapshotDO>getArgument(0).setId(99L); return 1; })
                .when(snapshotMapper).insert(any(CalendarNotifySnapshotDO.class));
        var row = course();
        var original = service.captureCourse(row, "CREATED", "ACTIVE");
        var state = new CalendarNotifyStateDO().setId(1L).setCalendarVersion(1).setCurrentSnapshotId(99L);
        when(stateMapper.selectForUpdate("COURSE", 7L)).thenReturn(state);
        row.setCalendarVersion(2);
        var cancelled = service.captureCourse(row, "DELETED", "DELETED");
        assertEquals("课程（历史直播）", original.getTitleSnapshot());
        assertEquals("2026-10-01 09:00 - 2026-10-01 10:00", original.getTimeSnapshot());
        assertEquals("ACTIVE", original.getRecordStatus());
        assertEquals("DELETED", cancelled.getRecordStatus());
        assertEquals(10L, cancelled.getTenantId());
        assertEquals("DELETED", state.getRecordStatus());
        assertNotEquals(original.getContentHash(), cancelled.getContentHash());
        verify(snapshotMapper, never()).updateById(any(CalendarNotifySnapshotDO.class));
    }

    @Test void repeatedCaptureReusesOriginalSnapshotAndRejectsChangedContentAtSameVersion() {
        var original = service.captureCourse(course(), "CREATED", "ACTIVE");
        original.setId(99L);
        when(snapshotMapper.selectVersion("COURSE", 7L, 1)).thenReturn(original);
        when(stateMapper.selectForUpdate("COURSE", 7L)).thenReturn(new CalendarNotifyStateDO().setId(1L).setCalendarVersion(1));
        clearInvocations(snapshotMapper, stateMapper);
        assertSame(original, service.captureCourse(course(), "MANUAL", "ACTIVE"));
        assertEquals("CREATED", original.getEventType());
        assertEquals(CALENDAR_NOTIFY_SNAPSHOT_CONFLICT.getCode(), assertThrows(ServiceException.class,
                () -> service.captureCourse(course().setRemark("改变备注"), "UPDATED", "ACTIVE")).getCode());
        verify(snapshotMapper, never()).insert(any(CalendarNotifySnapshotDO.class));
        verify(snapshotMapper, never()).updateById(any(CalendarNotifySnapshotDO.class));
    }

    @Test void stateCannotMoveBackToOlderVersion() {
        when(stateMapper.selectForUpdate("COURSE", 7L)).thenReturn(new CalendarNotifyStateDO().setCalendarVersion(3));
        assertThrows(ServiceException.class, () -> service.captureCourse(course(), "MANUAL", "ACTIVE"));
        verifyNoInteractions(snapshotMapper);
    }

    @Test void examTitleIncludesFrozenSpecificationLabels() {
        var row = new ExamScheduleDO().setId(8L).setCalendarVersion(2).setProductId(12L)
                .setProductNameSnapshot("历史产品").setRecordStatus("PUBLISHED").setScheduleType("EXACT")
                .setExactDate(LocalDate.of(2026, 10, 1)).setFrozenSkusJson("[]")
                .setSelectedSpecsJson(JsonUtils.toJsonString(List.of(new ProductSpecVO("level", "考试等级", "2", "二级", false))));
        var snapshot = service.captureExam(row, "PUBLISHED");
        assertEquals("历史产品，考试等级：二级", snapshot.getTitleSnapshot());
        assertEquals("2026-10-01", snapshot.getTimeSnapshot());
        assertTrue(snapshot.getDetailsJson().contains("frozenSkusJson"));
    }

    private CourseCalendarEventDO course() {
        return new CourseCalendarEventDO().setId(7L).setCalendarVersion(1).setCourseName("课程")
                .setCourseFormValue("LIVE").setCourseFormLabelSnapshot("历史直播")
                .setStartTime(LocalDateTime.of(2026, 10, 1, 9, 0)).setEndTime(LocalDateTime.of(2026, 10, 1, 10, 0));
    }
}
