package cn.iocoder.yudao.module.zsjos.service.calendarrestday;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import cn.iocoder.yudao.module.zsjos.controller.admin.calendarrestday.CalendarRestDayController;
import cn.iocoder.yudao.module.zsjos.controller.admin.calendarrestday.vo.*;
import cn.iocoder.yudao.module.zsjos.dal.mysql.calendarrestday.CalendarRestDayMapper;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.calendarrestday.CalendarRestDayDO;
import org.junit.jupiter.api.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.test.util.ReflectionTestUtils;
import java.time.LocalDate;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CalendarRestDayServiceTest {
    CalendarRestDayMapper mapper = mock(CalendarRestDayMapper.class);
    PermissionApi permissions = mock(PermissionApi.class);
    CalendarRestDayPermissionProvider access = new CalendarRestDayPermissionProvider();
    CalendarRestDayService service = new CalendarRestDayService();
    LocalDate date = LocalDate.of(2026, 10, 1);
    @BeforeAll static void metadata() {
        com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(new org.apache.ibatis.builder.MapperBuilderAssistant(new com.baomidou.mybatisplus.core.MybatisConfiguration(), "rest-test"), CalendarRestDayDO.class);
    }
    @BeforeEach void setup() {
        TenantContextHolder.setTenantId(1L);
        ReflectionTestUtils.setField(access, "permissions", permissions);
        ReflectionTestUtils.setField(service, "access", access);
        ReflectionTestUtils.setField(service, "mapper", mapper);
    }
    @AfterEach void clear() { TenantContextHolder.clear(); }
    CalendarRestDayBatchReqVO req(boolean rest, long version) {
        return new CalendarRestDayBatchReqVO().setChanges(List.of(new CalendarRestDayChangeVO().setDate(date).setRest(rest).setVersion(version)));
    }
    void allow() { when(permissions.hasAnyPermissions(7L, CalendarRestDayPermissionProvider.MANAGE)).thenReturn(true); }
    CalendarRestDayDO row(boolean rest, long version) {
        var row = new CalendarRestDayDO().setId(1L).setDate(date).setRest(rest).setVersion(version);
        row.setTenantId(1L); row.setDeleted(false); return row;
    }
    @Test void deniedAndForeignTenantNeverReachPersistence() {
        assertThrows(ServiceException.class, () -> service.update(1L,7L,req(true,0)));
        assertThrows(ServiceException.class, () -> service.list(1L,7L,date,date));
        assertThrows(ServiceException.class, () -> service.update(2L,7L,req(true,0)));
        verifyNoInteractions(mapper);
    }
    @Test void homeReadDoesNotNeedManageOrPersonalCalendar() {
        when(permissions.hasAnyPermissions(7L, CalendarRestDayPermissionProvider.READ)).thenReturn(true);
        when(mapper.selectList(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class))).thenReturn(List.of(row(true,1)));
        assertTrue(service.list(1L,7L,date,date).getFirst().getRest());
        assertThrows(ServiceException.class, () -> service.update(1L,7L,req(false,1)));
    }
    @Test void validatesDuplicateDatesBeforeWrites() {
        allow(); var request=req(true,0); request.setChanges(List.of(request.getChanges().getFirst(),request.getChanges().getFirst()));
        assertThrows(ServiceException.class, () -> service.update(1L,7L,request)); verifyNoInteractions(mapper);
    }
    @Test void conflictingVersionIsNotOverwritten() {
        allow(); when(mapper.lock(1L,date)).thenReturn(row(true,3));
        var error=assertThrows(ServiceException.class, () -> service.update(1L,7L,req(false,0)));
        assertEquals(1900018061,error.getCode()); verify(mapper,never()).updateById(any(CalendarRestDayDO.class));
    }
    @Test void identicalRetryDoesNotAdvanceVersion() {
        allow(); when(mapper.lock(1L,date)).thenReturn(row(true,1));
        assertEquals(1L,service.update(1L,7L,req(true,0)).getFirst().getVersion());
        verify(mapper,never()).updateById(any(CalendarRestDayDO.class));
    }
    @Test void cancelRetainsRowAndAdvancesVersion() {
        allow(); when(mapper.lock(1L,date)).thenReturn(row(true,1));
        var result=service.update(1L,7L,req(false,1)).getFirst();
        assertFalse(result.getRest()); assertEquals(2L,result.getVersion()); verify(mapper).updateById(any(CalendarRestDayDO.class));
    }
    @Test void rangeIsBoundedAndOrdered() {
        when(permissions.hasAnyPermissions(7L, CalendarRestDayPermissionProvider.READ)).thenReturn(true);
        assertThrows(ServiceException.class, () -> service.list(1L,7L,date,date.minusDays(1)));
        assertThrows(ServiceException.class, () -> service.list(1L,7L,date,date.plusDays(366)));
        verifyNoInteractions(mapper);
    }
    @Test void controllerHasIndependentWritePermission() throws Exception {
        var annotation=CalendarRestDayController.class.getMethod("update",CalendarRestDayBatchReqVO.class).getAnnotation(PreAuthorize.class);
        assertEquals("@ss.hasPermission('zsjos:calendar-rest-day:manage')",annotation.value());
    }
}
