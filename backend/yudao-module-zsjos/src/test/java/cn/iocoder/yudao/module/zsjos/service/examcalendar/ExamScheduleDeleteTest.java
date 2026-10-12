package cn.iocoder.yudao.module.zsjos.service.examcalendar;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.examcalendar.ExamScheduleDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.examcalendar.ExamScheduleMapper;
import cn.iocoder.yudao.module.zsjos.service.calendar.CalendarNotificationSnapshotService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.time.LocalDate;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ExamScheduleDeleteTest {
    @InjectMocks private ExamScheduleService service;
    @Mock private ExamScheduleMapper mapper;
    @Mock private PermissionApi permissionApi;
    @Mock private CalendarNotificationSnapshotService snapshots;

    @Test void deletesExpiredAndMultiDayDraftsWithoutNotifications() {
        when(permissionApi.hasAnyPermissions(20L, ExamScheduleService.PERMISSION_MANAGE)).thenReturn(true);
        when(mapper.selectForUpdate(9L)).thenReturn(new ExamScheduleDO().setId(9L)
                .setRecordStatus("DRAFT").setScheduleType("EXACT").setExactDate(LocalDate.of(2000, 1, 1)));
        when(mapper.selectForUpdate(10L)).thenReturn(new ExamScheduleDO().setId(10L)
                .setRecordStatus("DRAFT").setScheduleType("MULTI_DAY"));
        service.deleteDraft(9L, 20L);
        service.deleteDraft(10L, 20L);
        var order = inOrder(mapper);
        order.verify(mapper).selectForUpdate(9L);
        order.verify(mapper).deleteById(9L);
        order.verify(mapper).selectForUpdate(10L);
        order.verify(mapper).deleteById(10L);
        verifyNoInteractions(snapshots);
    }

    @Test void rejectsPublishedRevokedAndMissingRecords() {
        when(permissionApi.hasAnyPermissions(20L, ExamScheduleService.PERMISSION_MANAGE)).thenReturn(true);
        for (String status : new String[]{"PUBLISHED", "REVOKED"}) {
            when(mapper.selectForUpdate(9L)).thenReturn(new ExamScheduleDO().setId(9L).setRecordStatus(status));
            assertEquals(1_900_018_005, assertThrows(ServiceException.class, () -> service.deleteDraft(9L, 20L)).getCode());
        }
        when(mapper.selectForUpdate(9L)).thenReturn(null);
        assertEquals(1_900_018_001, assertThrows(ServiceException.class, () -> service.deleteDraft(9L, 20L)).getCode());
        verify(mapper, never()).deleteById(anyLong());
        verifyNoInteractions(snapshots);
    }

    @Test void deniedManagementPermissionDoesNotReadOrWrite() {
        assertEquals(1_900_018_006, assertThrows(ServiceException.class, () -> service.deleteDraft(9L, 20L)).getCode());
        verifyNoInteractions(mapper, snapshots);
    }
}
