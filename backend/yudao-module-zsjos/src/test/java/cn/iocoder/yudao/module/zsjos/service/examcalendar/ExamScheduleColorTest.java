package cn.iocoder.yudao.module.zsjos.service.examcalendar;

import cn.iocoder.yudao.module.infra.api.config.ConfigApi;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import cn.iocoder.yudao.module.zsjos.controller.admin.examcalendar.vo.*;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.examcalendar.ExamScheduleDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.examcalendar.ExamScheduleMapper;
import cn.iocoder.yudao.module.zsjos.service.calendar.CalendarNotificationSnapshotService;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import java.time.LocalDate;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ExamScheduleColorTest {
    @InjectMocks ExamScheduleService service;
    @Mock ExamScheduleMapper mapper;
    @Mock PermissionApi permissionApi;
    @Mock ConfigApi configApi;
    @Mock CalendarNotificationSnapshotService notificationSnapshots;

    @BeforeAll static void metadata() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), "exam-color"), ExamScheduleDO.class);
    }
    ExamScheduleSaveReqVO request(String color) {
        return new ExamScheduleSaveReqVO().setScheduleName("自选颜色考试").setScheduleType("EXACT")
            .setExactDate(LocalDate.of(2099, 10, 10)).setBackgroundColor(color);
    }
    @Test void createPersistsNormalizedColorAndQueriesExposeIt() {
        when(permissionApi.hasAnyPermissions(20L, ExamScheduleService.PERMISSION_MANAGE)).thenReturn(true);
        service.create(request("#aAbB12"), 20L);
        var saved = ArgumentCaptor.forClass(ExamScheduleDO.class);
        verify(mapper).insert(saved.capture());
        assertEquals("#AABB12", saved.getValue().getBackgroundColor());
        var page = new ExamSchedulePageReqVO(); page.setPageNo(1); page.setPageSize(10);
        when(mapper.selectExactList(eq(page), eq(true), any())).thenReturn(List.of(saved.getValue().setId(1L)));
        assertEquals("#AABB12", service.exactPage(page, 20L).getList().getFirst().getBackgroundColor());
    }
    @Test void emptyColorCreatesDefaultAndUnsafeCssNeverReachesMapper() {
        when(permissionApi.hasAnyPermissions(20L, ExamScheduleService.PERMISSION_MANAGE)).thenReturn(true);
        service.create(request(""), 20L);
        var saved = ArgumentCaptor.forClass(ExamScheduleDO.class); verify(mapper).insert(saved.capture());
        assertNull(saved.getValue().getBackgroundColor());
        clearInvocations(mapper, notificationSnapshots);
        for (String invalid : List.of("red", "#fff", "#11223344", "url(https://example.com)", "#123456;display:none")) {
            assertThrows(jakarta.validation.ConstraintViolationException.class, () -> service.create(request(invalid), 20L));
        }
        verifyNoInteractions(mapper, notificationSnapshots);
    }
    @Test void updateCanClearOrPreserveColorWithoutChangingNotificationVersion() {
        when(permissionApi.hasAnyPermissions(20L, ExamScheduleService.PERMISSION_MANAGE)).thenReturn(true);
        var req = request(null);
        var current = new ExamScheduleDO().setId(9L).setScheduleName(req.getScheduleName()).setScheduleType("EXACT")
            .setExactDate(req.getExactDate()).setRecordStatus("DRAFT").setCalendarVersion(5).setBackgroundColor("#112233");
        when(mapper.selectForUpdate(9L)).thenReturn(current);
        service.update(9L, req, 20L);
        service.update(9L, req.setBackgroundColor(""), 20L);
        service.update(9L, req.setBackgroundColor("#abcdef"), 20L);
        var saved = ArgumentCaptor.forClass(ExamScheduleDO.class); verify(mapper, times(3)).update(saved.capture(), any());
        assertEquals("#112233", saved.getAllValues().get(0).getBackgroundColor());
        assertNull(saved.getAllValues().get(1).getBackgroundColor());
        assertEquals("#ABCDEF", saved.getAllValues().get(2).getBackgroundColor());
        saved.getAllValues().forEach(row -> assertEquals(5, row.getCalendarVersion()));
    }
}
