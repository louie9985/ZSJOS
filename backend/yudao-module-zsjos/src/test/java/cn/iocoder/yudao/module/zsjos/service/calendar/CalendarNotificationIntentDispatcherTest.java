package cn.iocoder.yudao.module.zsjos.service.calendar;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.framework.tenant.core.service.TenantFrameworkService;
import cn.iocoder.yudao.module.system.api.maintenance.MaintenanceModeApi;
import cn.iocoder.yudao.module.zsjos.dal.mysql.calendar.CalendarNotifyIntentMapper;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CalendarNotificationIntentDispatcherTest {
    @InjectMocks private CalendarNotificationIntentDispatcher dispatcher;
    @Mock private CalendarNotificationIntentRecoveryService recovery;
    @Mock private CalendarNotifyIntentMapper mapper;
    @Mock private TenantFrameworkService tenants;
    @Mock private MaintenanceModeApi maintenance;
    @AfterEach void cleanup() { TenantContextHolder.clear(); }

    @Test void immediateFailureCannotTurnCommittedMaintenanceIntoAnError() {
        TenantContextHolder.setTenantId(99L);
        var failure = new IllegalStateException("not logged");
        doAnswer(call -> { assertEquals(10L, TenantContextHolder.getTenantId()); throw failure; }).when(recovery).recover(5L);
        doThrow(new IllegalStateException()).when(recovery).recordFailure(5L, failure);
        assertDoesNotThrow(() -> dispatcher.afterCommit(new CalendarNotificationIntentService.Ready(10L, 5L)));
        assertEquals(99L, TenantContextHolder.getTenantId());
    }
    @Test void maintenanceModeDefersBothImmediateAndScheduledAcceptance() {
        when(maintenance.isEnabled()).thenReturn(true);
        dispatcher.afterCommit(new CalendarNotificationIntentService.Ready(10L, 5L)); dispatcher.recoverPending();
        verifyNoInteractions(recovery, mapper, tenants);
    }
    @Test void boundedTenantScanContinuesAfterOneIntentFailureAndRestoresContext() {
        TenantContextHolder.setTenantId(99L);
        when(tenants.getTenantIds()).thenReturn(List.of(10L, 11L));
        when(mapper.selectDue(eq(10L), any())).thenReturn(List.of(1L, 2L));
        when(mapper.selectDue(eq(11L), any())).thenReturn(List.of(3L));
        doThrow(new IllegalStateException()).when(recovery).recover(1L);
        doAnswer(call -> { assertEquals(10L, TenantContextHolder.getTenantId()); return null; }).when(recovery).recover(2L);
        doAnswer(call -> { assertEquals(11L, TenantContextHolder.getTenantId()); return null; }).when(recovery).recover(3L);
        dispatcher.recoverPending();
        verify(recovery).recordFailure(eq(1L), any()); verify(recovery).recover(2L); verify(recovery).recover(3L);
        assertEquals(99L, TenantContextHolder.getTenantId());
    }
}
