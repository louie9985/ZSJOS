package cn.iocoder.yudao.module.zsjos.service.personalcalendar;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.module.zsjos.controller.admin.personalcalendar.vo.PersonalCalendarEventListReqVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.personalcalendar.vo.PersonalCalendarEventSaveReqVO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.personalcalendar.PersonalCalendarEventDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.personalcalendar.PersonalCalendarEventMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PersonalCalendarEventServiceTest {
    @InjectMocks private PersonalCalendarEventService service;
    @Mock private PersonalCalendarEventMapper mapper;
    @Mock private cn.iocoder.yudao.module.zsjos.service.common.BusinessReadScopeService readScopeService;

    @Test
    void allReadDoesNotGrantWriteOwnership() {
        var req = listReq();
        req.setReadScope("ALL");
        when(readScopeService.resolve("ALL", null, 20L)).thenReturn(null);
        when(mapper.selectReadRange(null, req.getRangeStart(), req.getRangeEnd())).thenReturn(List.of());
        assertTrue(service.list(req, 20L).isEmpty());
        verify(mapper).selectReadRange(null, req.getRangeStart(), req.getRangeEnd());
        when(mapper.selectById(9L)).thenReturn(new PersonalCalendarEventDO().setId(9L).setOwnerUserId(21L));
        assertThrows(ServiceException.class, () -> service.update(9L, saveReq(), 20L));
        assertThrows(ServiceException.class, () -> service.delete(9L, 20L));
        verify(mapper, never()).updateOwned(any(), anyLong());
        verify(mapper, never()).deleteOwned(anyLong(), anyLong(), any());
    }

    @Test
    void listAlwaysUsesCurrentUserAsOwner() {
        PersonalCalendarEventListReqVO req = listReq();
        when(mapper.selectMyRange(20L, req.getRangeStart(), req.getRangeEnd())).thenReturn(List.of());
        assertTrue(service.list(req, 20L).isEmpty());
        verify(mapper).selectMyRange(20L, req.getRangeStart(), req.getRangeEnd());
    }

    @Test
    void createForcesOwnerAndManualSource() {
        PersonalCalendarEventSaveReqVO req = saveReq();
        doAnswer(invocation -> { invocation.<PersonalCalendarEventDO>getArgument(0).setId(9L); return 1; })
                .when(mapper).insert(any(PersonalCalendarEventDO.class));
        assertEquals(9L, service.create(req, 20L));
        ArgumentCaptor<PersonalCalendarEventDO> event = ArgumentCaptor.forClass(PersonalCalendarEventDO.class);
        verify(mapper).insert(event.capture());
        assertEquals(20L, event.getValue().getOwnerUserId());
        assertEquals("MANUAL", event.getValue().getSourceType());
    }

    @Test
    void updateAndDeleteRejectOtherUsersEvent() {
        when(mapper.selectById(9L)).thenReturn(new PersonalCalendarEventDO().setId(9L).setOwnerUserId(21L));
        assertEquals(1_900_017_002, assertThrows(ServiceException.class,
                () -> service.update(9L, saveReq(), 20L)).getCode());
        assertEquals(1_900_017_002, assertThrows(ServiceException.class,
                () -> service.delete(9L, 20L)).getCode());
        verify(mapper, never()).updateOwned(any(PersonalCalendarEventDO.class), anyLong());
        verify(mapper, never()).deleteOwned(anyLong(), anyLong(), any());
    }

    @Test
    void updateKeepsOwnerInWriteCondition() {
        PersonalCalendarEventSaveReqVO req = saveReq();
        when(mapper.selectById(9L)).thenReturn(new PersonalCalendarEventDO().setId(9L).setOwnerUserId(20L));

        service.update(9L, req, 20L);

        ArgumentCaptor<PersonalCalendarEventDO> update = ArgumentCaptor.forClass(PersonalCalendarEventDO.class);
        verify(mapper).updateOwned(update.capture(), eq(20L));
        assertEquals(9L, update.getValue().getId());
        assertEquals("复盘", update.getValue().getTitle());
    }

    @Test
    void allowsZeroLengthAndRejectsNegativeTimeRange() {
        PersonalCalendarEventSaveReqVO req = saveReq();
        req.setEndTime(req.getStartTime());
        doAnswer(invocation -> { invocation.<PersonalCalendarEventDO>getArgument(0).setId(10L); return 1; })
                .when(mapper).insert(any(PersonalCalendarEventDO.class));
        assertEquals(10L, service.create(req, 20L));

        req.setEndTime(req.getStartTime().minusMinutes(1));
        assertEquals(1_900_017_003, assertThrows(ServiceException.class,
                () -> service.create(req, 20L)).getCode());
        verify(mapper, times(1)).insert(any(PersonalCalendarEventDO.class));
    }

    private static PersonalCalendarEventListReqVO listReq() {
        PersonalCalendarEventListReqVO req = new PersonalCalendarEventListReqVO();
        req.setRangeStart(LocalDateTime.of(2026, 9, 1, 0, 0));
        req.setRangeEnd(LocalDateTime.of(2026, 10, 1, 0, 0));
        return req;
    }

    private static PersonalCalendarEventSaveReqVO saveReq() {
        PersonalCalendarEventSaveReqVO req = new PersonalCalendarEventSaveReqVO();
        req.setTitle("复盘"); req.setDescription("月度复盘"); req.setAllDay(false);
        req.setStartTime(LocalDateTime.of(2026, 9, 7, 9, 0));
        req.setEndTime(LocalDateTime.of(2026, 9, 7, 10, 0));
        return req;
    }
}
