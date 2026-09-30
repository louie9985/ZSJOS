package cn.iocoder.yudao.module.zsjos.service.examcalendar;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import cn.iocoder.yudao.module.zsjos.controller.admin.examcalendar.vo.ExamSchedulePageReqVO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.examcalendar.ExamScheduleDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.examcalendar.ExamScheduleMapper;
import cn.iocoder.yudao.module.zsjos.service.calendar.CalendarNotificationSnapshotService;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import java.time.*;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ExamScheduleReeditTest {
    @Mock ExamScheduleMapper mapper;
    @Mock PermissionApi permissions;
    @Mock CalendarNotificationSnapshotService snapshots;
    @InjectMocks ExamScheduleService service;
    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 9, 30, 10, 0);
    private static final String KEY = "request-key-00001";

    @BeforeAll static void metadata() {
        com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(
            new org.apache.ibatis.builder.MapperBuilderAssistant(new com.baomidou.mybatisplus.core.MybatisConfiguration(), "reedit-test"), ExamScheduleDO.class);
    }
    @BeforeEach void setup() {
        TenantContextHolder.setTenantId(10L);
        ReflectionTestUtils.setField(service, "clock", Clock.fixed(NOW.atZone(ZONE).toInstant(), ZONE));
    }
    @AfterEach void cleanup() { TenantContextHolder.clear(); }
    private ExamScheduleDO row() {
        var row = new ExamScheduleDO().setId(9L).setRevokedBy(20L).setRevokedAt(NOW.minusSeconds(299))
            .setRecordStatus("REVOKED").setScheduleName("原名称").setScheduleType("EXACT")
            .setExactDate(LocalDate.of(2026, 10, 1)).setRemark("原备注");
        row.setTenantId(10L); row.setDeleted(false); return row;
    }
    private void allow(ExamScheduleDO row) {
        when(permissions.hasAnyPermissions(20L, ExamScheduleService.PERMISSION_MANAGE)).thenReturn(true);
        when(mapper.selectReeditRecordForUpdate(9L, 10L)).thenReturn(row);
    }
    @Test void claimsAt299SecondsAndSoftDeletesWithoutCreatingOrNotifying() {
        allow(row());
        var response = service.reedit(9L, KEY, 20L);
        assertEquals("原名称", response.getScheduleName()); assertEquals("原备注", response.getRemark());
        assertEquals(LocalDate.of(2026,10,1), response.getExactDate());
        var capture = ArgumentCaptor.forClass(LambdaUpdateWrapper.class);
        verify(mapper).update(isNull(), capture.capture());
        assertTrue(capture.getValue().getSqlSet().contains("deleted="));
        assertTrue(capture.getValue().getParamNameValuePairs().containsValue(KEY));
        assertTrue(capture.getValue().getParamNameValuePairs().containsValue(true));
        verify(mapper, never()).insert(any(ExamScheduleDO.class)); verifyNoInteractions(snapshots);
    }
    @Test void rejectsAt300Seconds() {
        allow(row().setRevokedAt(NOW.minusMinutes(5)));
        assertEquals(1_900_018_011, assertThrows(ServiceException.class, () -> service.reedit(9L, KEY, 20L)).getCode());
        verify(mapper, never()).update(any(), any(Wrapper.class));
    }
    @Test void legacyRevokedRecordHasNoInventedWindow() {
        allow(row().setRevokedAt(null));
        assertEquals(1_900_018_011, assertThrows(ServiceException.class, () -> service.reedit(9L, KEY, 20L)).getCode());
    }
    @Test void sameKeyReplaysAfterExpiryButDifferentKeyCannotClaim() {
        var row = row().setReeditClaimedAt(NOW.minusSeconds(1)).setRevokedAt(NOW.minusHours(1)).setReeditOperationKey(KEY);
        row.setDeleted(true); allow(row);
        assertEquals("原名称", service.reedit(9L, KEY, 20L).getScheduleName());
        assertEquals(1_900_018_012, assertThrows(ServiceException.class, () -> service.reedit(9L, "another-request-0002", 20L)).getCode());
        verify(mapper, never()).update(any(), any(Wrapper.class)); verifyNoInteractions(snapshots);
    }
    @Test void rejectsOtherRevokerAndForeignTenantEvenWithManage() {
        var row = row().setRevokedBy(21L); allow(row);
        assertEquals(1_900_018_006, assertThrows(ServiceException.class, () -> service.reedit(9L, KEY, 20L)).getCode());
        row.setRevokedBy(20L); row.setTenantId(11L);
        assertEquals(1_900_018_001, assertThrows(ServiceException.class, () -> service.reedit(9L, KEY, 20L)).getCode());
        verify(mapper, never()).update(any(), any(Wrapper.class));
    }
    @Test void permissionRemovalPreventsReplayBeforeReading() {
        assertEquals(1_900_018_006, assertThrows(ServiceException.class, () -> service.reedit(9L, KEY, 20L)).getCode());
        verifyNoInteractions(mapper);
    }
    @Test void requiresRevokedLifecycle() {
        allow(row().setRecordStatus("PUBLISHED"));
        assertEquals(1_900_018_005, assertThrows(ServiceException.class, () -> service.reedit(9L, KEY, 20L)).getCode());
    }
    @Test void preservesMultiDayDatesAndLegacyStoredTitle() {
        var row = row().setScheduleType("MULTI_DAY").setExactDate(null).setStartDate(LocalDate.of(2026,10,1))
            .setEndDate(LocalDate.of(2026,10,5)).setScheduleName(null).setCategoryNameSnapshot("历史考试");
        allow(row); var result = service.reedit(9L, KEY, 20L);
        assertEquals("历史考试", result.getScheduleName()); assertNull(result.getExactDate());
        assertEquals(row.getStartDate(), result.getStartDate()); assertEquals(row.getEndDate(), result.getEndDate());
    }
    @Test void responseUsesServerTimeAndOwnerOnlyAction() {
        when(permissions.hasAnyPermissions(20L, ExamScheduleService.PERMISSION_MANAGE)).thenReturn(true);
        var req = new ExamSchedulePageReqVO(); req.setPageNo(1); req.setPageSize(20);
        var row = row();
        when(mapper.selectExactList(eq(req), eq(true), eq(NOW))).thenReturn(List.of(row));
        var response = service.exactPage(req, 20L).getList().getFirst();
        assertEquals(NOW, response.getServerTime()); assertEquals(NOW.plusSeconds(1), response.getReeditDeadline()); assertTrue(response.isCanReedit());
        row.setRevokedBy(21L);
        assertFalse(service.exactPage(req, 20L).getList().getFirst().isCanReedit());
    }
}
