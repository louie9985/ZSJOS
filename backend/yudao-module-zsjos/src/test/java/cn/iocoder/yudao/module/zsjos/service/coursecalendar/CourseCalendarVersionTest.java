package cn.iocoder.yudao.module.zsjos.service.coursecalendar;

import cn.iocoder.yudao.module.infra.api.file.FileApi;
import cn.iocoder.yudao.module.system.api.dict.DictDataApi;
import cn.iocoder.yudao.module.zsjos.controller.admin.coursecalendar.vo.CourseCalendarSaveReqVO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.coursecalendar.CourseCalendarEventDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.coursecalendar.CourseCalendarEventMapper;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import java.time.LocalDateTime;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CourseCalendarVersionTest {
    @InjectMocks private CourseCalendarEventService service;
    @Mock private CourseCalendarEventMapper mapper;
    @Mock private DictDataApi dictionaries;
    @Mock private FileApi files;
    @Mock private cn.iocoder.yudao.module.zsjos.service.calendar.CalendarNotificationSnapshotService notificationSnapshots;

    @BeforeAll static void metadata() {
        com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(
                new org.apache.ibatis.builder.MapperBuilderAssistant(new com.baomidou.mybatisplus.core.MybatisConfiguration(), "course-version"), CourseCalendarEventDO.class);
    }

    @Test void unchangedContentKeepsVersionAndHistoricalDictionaryLabel() {
        when(mapper.selectForUpdate(1L)).thenReturn(current());
        service.update(1L, request());
        var saved = ArgumentCaptor.forClass(CourseCalendarEventDO.class);
        verify(mapper).update(saved.capture(), any(Wrapper.class));
        assertEquals(4, saved.getValue().getCalendarVersion());
        assertEquals("历史直播", saved.getValue().getCourseFormLabelSnapshot());
        verifyNoInteractions(dictionaries, files);
        verify(mapper, never()).selectById(anyLong());
    }

    @Test void changedTimeIncrementsOnceAndUsesLockedCurrentVersion() {
        when(mapper.selectForUpdate(1L)).thenReturn(current());
        var req = request(); req.setEndTime(req.getEndTime().plusHours(1));
        service.update(1L, req);
        var saved = ArgumentCaptor.forClass(CourseCalendarEventDO.class);
        verify(mapper).update(saved.capture(), any(Wrapper.class));
        assertEquals(5, saved.getValue().getCalendarVersion());
    }

    @Test void clearingRemarkIsPersistedAndAdvancesVersion() {
        when(mapper.selectForUpdate(1L)).thenReturn(current());
        var req = request(); req.setRemark(null);
        service.update(1L, req);
        var saved = ArgumentCaptor.forClass(CourseCalendarEventDO.class);
        var wrapper = ArgumentCaptor.forClass(com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper.class);
        verify(mapper).update(saved.capture(), wrapper.capture());
        assertEquals(5, saved.getValue().getCalendarVersion());
        assertTrue(wrapper.getValue().getSqlSet().contains("remark="));
        assertNull(saved.getValue().getRemark());
    }

    @Test void previewRetainsHistoricalLabelAndDoesNotMutateOrLock() {
        var original = current();
        when(mapper.selectById(1L)).thenReturn(original);
        var unchanged = service.previewSave(1L, request());
        assertEquals(4, unchanged.getCalendarVersion());
        assertEquals("历史直播", unchanged.getCourseFormLabelSnapshot());
        var changed = request(); changed.setEndTime(changed.getEndTime().plusHours(1));
        var preview = service.previewSave(1L, changed);
        assertEquals(5, preview.getCalendarVersion());
        assertEquals(4, original.getCalendarVersion());
        assertEquals(LocalDateTime.of(2026, 10, 1, 10, 0), original.getEndTime());
        verify(mapper, times(2)).selectById(1L);
        verifyNoMoreInteractions(mapper);
        verifyNoInteractions(dictionaries, files, notificationSnapshots);
    }

    @Test void deletionAdvancesVersionBeforeLogicalDeletion() {
        when(mapper.selectForUpdate(1L)).thenReturn(current());
        service.delete(1L);
        var ordered = inOrder(mapper);
        ordered.verify(mapper).selectForUpdate(1L);
        var saved = ArgumentCaptor.forClass(CourseCalendarEventDO.class);
        ordered.verify(mapper).updateById(saved.capture());
        ordered.verify(mapper).deleteById(1L);
        assertEquals(5, saved.getValue().getCalendarVersion());
    }

    private CourseCalendarEventDO current() {
        return new CourseCalendarEventDO().setId(1L).setCalendarVersion(4).setCourseName("课程")
                .setCourseFormValue("LIVE").setCourseFormLabelSnapshot("历史直播")
                .setStartTime(LocalDateTime.of(2026, 10, 1, 9, 0))
                .setEndTime(LocalDateTime.of(2026, 10, 1, 10, 0)).setRemark("备注");
    }
    private CourseCalendarSaveReqVO request() {
        var row = current();
        var req = new CourseCalendarSaveReqVO();
        req.setCourseName(" 课程 "); req.setCourseFormValue(row.getCourseFormValue());
        req.setStartTime(row.getStartTime()); req.setEndTime(row.getEndTime());
        req.setRemark(row.getRemark()); req.setAttachmentIds(List.of());
        return req;
    }
}
