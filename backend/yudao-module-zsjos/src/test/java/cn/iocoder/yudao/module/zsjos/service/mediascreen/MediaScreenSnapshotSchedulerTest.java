package cn.iocoder.yudao.module.zsjos.service.mediascreen;

import cn.iocoder.yudao.framework.tenant.core.service.TenantFrameworkService;
import cn.iocoder.yudao.module.system.api.maintenance.MaintenanceModeApi;
import cn.iocoder.yudao.module.zsjos.framework.mediascreen.MediaScreenProperties;
import org.junit.jupiter.api.Test;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;

import static org.mockito.Mockito.*;

class MediaScreenSnapshotSchedulerTest {
    @Test void freezesPreviousDayForEveryTenantAfterConfiguredTime(){
        TenantFrameworkService tenants=mock(TenantFrameworkService.class);when(tenants.getTenantIds()).thenReturn(List.of(1L,2L));
        MaintenanceModeApi maintenance=mock(MaintenanceModeApi.class);MediaScreenQueryService service=mock(MediaScreenQueryService.class);MediaScreenProperties properties=new MediaScreenProperties();properties.setEnabled(true);properties.getSnapshot().setHour(4);properties.getSnapshot().setMinute(0);
        var scheduler=new MediaScreenSnapshotScheduler(tenants,maintenance,service,properties);var now=ZonedDateTime.of(2026,8,26,4,1,0,0,ZoneId.of("Asia/Shanghai"));

        scheduler.freezeDueDate(now);

        verify(service).freeze(1L,now.toLocalDate().minusDays(1));verify(service).freeze(2L,now.toLocalDate().minusDays(1));
    }

    @Test void doesNothingWhileFeatureDisabled(){
        TenantFrameworkService tenants=mock(TenantFrameworkService.class);var service=mock(MediaScreenQueryService.class);var scheduler=new MediaScreenSnapshotScheduler(tenants,mock(MaintenanceModeApi.class),service,new MediaScreenProperties());
        scheduler.freezeDueDate(ZonedDateTime.now(ZoneId.of("Asia/Shanghai")));
        verifyNoInteractions(tenants,service);
    }
}
