package cn.iocoder.yudao.module.zsjos.service.lead;

import cn.iocoder.yudao.framework.tenant.core.service.TenantFrameworkService;
import cn.iocoder.yudao.module.system.api.maintenance.MaintenanceModeApi;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LeadAssignmentTimeoutSchedulerTest {
    @InjectMocks private LeadAssignmentTimeoutScheduler scheduler;
    @Mock private TenantFrameworkService tenantFrameworkService;
    @Mock private LeadDispatchService dispatchService;
    @Mock private MaintenanceModeApi maintenanceModeApi;

    @Test
    void maintenanceModeSkipsAssignmentTimeoutWork() {
        when(maintenanceModeApi.isEnabled()).thenReturn(true);
        scheduler.processExpiredAssignments();
        verifyNoInteractions(tenantFrameworkService, dispatchService);
    }
}
