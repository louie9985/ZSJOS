package cn.iocoder.yudao.module.zsjos.service.lead;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.framework.tenant.core.service.TenantFrameworkService;
import cn.iocoder.yudao.module.system.api.maintenance.MaintenanceModeApi;
import cn.iocoder.yudao.module.zsjos.dal.mysql.lead.LeadPublicSeaTaskCleanupMapper;
import org.junit.jupiter.api.*;
import java.time.LocalDateTime;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import org.springframework.test.util.ReflectionTestUtils;

class LeadPublicSeaTaskCleanupServiceTest {
    @AfterEach void clear(){TenantContextHolder.clear();}
    @Test void emptyAndNonemptyScansAreTenantScoped() {
        TenantContextHolder.setTenantId(1L);
        var mapper=mock(LeadPublicSeaTaskCleanupMapper.class);
        var service=new LeadPublicSeaTaskCleanupService();ReflectionTestUtils.setField(service,"mapper",mapper);
        var now=LocalDateTime.now();
        when(mapper.selectCandidates(1L,null)).thenReturn(List.of());
        assertEquals(0,service.cancelObsolete(null,now));
        verify(mapper,never()).cancelObsolete(any(),any(),any(),any());
        when(mapper.selectCandidates(1L,7L)).thenReturn(List.of(8L,9L));
        when(mapper.cancelObsolete(eq(1L),eq(List.of(8L,9L)),eq(now),anyString())).thenReturn(2);
        assertEquals(2,service.cancelObsolete(7L,now));
    }
    @Test void schedulerRunsCleanupAfterPoolChangesEvenWhenScanFails() {
        var scheduler=new LeadPublicSeaPoolScheduler();
        var tenants=mock(TenantFrameworkService.class);var pools=mock(LeadPublicSeaPoolService.class);
        var cleanup=mock(LeadPublicSeaTaskCleanupService.class);var maintenance=mock(MaintenanceModeApi.class);
        ReflectionTestUtils.setField(scheduler,"tenantFrameworkService",tenants);
        ReflectionTestUtils.setField(scheduler,"service",pools);
        ReflectionTestUtils.setField(scheduler,"taskCleanupService",cleanup);
        ReflectionTestUtils.setField(scheduler,"maintenanceModeApi",maintenance);
        when(tenants.getTenantIds()).thenReturn(List.of(1L));
        when(pools.scanDue(any())).thenThrow(new IllegalStateException("fixture failure"));
        when(cleanup.cancelObsolete(isNull(),any())).thenAnswer(i->{assertEquals(1L,TenantContextHolder.getRequiredTenantId());return 1;});
        scheduler.process();
        var order=inOrder(pools,cleanup);order.verify(pools).clearInvalidCollaborators(any());
        order.verify(cleanup).cancelObsolete(isNull(),any());order.verify(pools).emitAdvanceReminders(any());
        when(maintenance.isEnabled()).thenReturn(true);clearInvocations(cleanup);scheduler.process();verifyNoInteractions(cleanup);
    }
}
