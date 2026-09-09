package cn.iocoder.yudao.module.zsjos.service.impersonation;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.framework.tenant.core.service.TenantFrameworkService;
import cn.iocoder.yudao.module.system.api.maintenance.MaintenanceModeApi;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ImpersonationSessionSchedulerTest {
    @InjectMocks private ImpersonationSessionScheduler scheduler;
    @Mock private MaintenanceModeApi maintenanceModeApi;
    @Mock private TenantFrameworkService tenantFrameworkService;
    @Mock private ImpersonationService service;

    @BeforeEach
    void setUp() {
        // 其他测试（如 LeadSupplementRemarkTest 的并发用例）会让线程池线程残留 tenant 上下文，
        // 若不清空，本测试的 TenantUtils.execute 会把泄漏的旧租户当作 oldTenantId 恢复，破坏断言。
        TenantContextHolder.clear();
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    void expireIdleSessionsExecutesForEveryTenantAndRestoresContext() {
        when(tenantFrameworkService.getTenantIds()).thenReturn(List.of(11L, 12L));
        List<Long> observedTenantIds = new ArrayList<>();
        doAnswer(invocation -> {
            observedTenantIds.add(TenantContextHolder.getRequiredTenantId());
            return 0;
        }).when(service).expireIdleSessions();

        scheduler.expireIdleSessions();

        assertEquals(List.of(11L, 12L), observedTenantIds);
        assertNull(TenantContextHolder.getTenantId());
    }

    @Test
    void expireIdleSessionsContinuesAfterTenantFailure() {
        when(tenantFrameworkService.getTenantIds()).thenReturn(List.of(11L, 12L));
        List<Long> observedTenantIds = new ArrayList<>();
        doAnswer(invocation -> {
            Long tenantId = TenantContextHolder.getRequiredTenantId();
            observedTenantIds.add(tenantId);
            if (tenantId.equals(11L)) {
                throw new IllegalStateException("tenant failure");
            }
            return 0;
        }).when(service).expireIdleSessions();

        scheduler.expireIdleSessions();

        assertEquals(List.of(11L, 12L), observedTenantIds);
        assertNull(TenantContextHolder.getTenantId());
    }

    @Test
    void expireIdleSessionsSkipsDuringMaintenance() {
        when(maintenanceModeApi.isEnabled()).thenReturn(true);

        scheduler.expireIdleSessions();

        verify(tenantFrameworkService, never()).getTenantIds();
        verify(service, never()).expireIdleSessions();
    }
}
