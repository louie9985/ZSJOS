package cn.iocoder.yudao.module.zsjos.service.mediascreen;

import cn.iocoder.yudao.framework.tenant.core.service.TenantFrameworkService;
import cn.iocoder.yudao.framework.tenant.core.util.TenantUtils;
import cn.iocoder.yudao.module.system.api.maintenance.MaintenanceModeApi;
import cn.iocoder.yudao.module.zsjos.framework.mediascreen.MediaScreenProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.*;

@Component
@Slf4j
@RequiredArgsConstructor
public class MediaScreenSnapshotScheduler {
    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    private final TenantFrameworkService tenantFrameworkService;
    private final MaintenanceModeApi maintenanceModeApi;
    private final MediaScreenQueryService service;
    private final MediaScreenProperties properties;

    @Scheduled(fixedDelayString = "${yudao.media-screen.snapshot.scan-delay-ms:300000}")
    public void freezeDueDate() {
        freezeDueDate(ZonedDateTime.now(ZONE));
    }

    void freezeDueDate(ZonedDateTime now) {
        if (!properties.isEnabled() || maintenanceModeApi.isEnabled()) return;
        LocalTime freezeTime = LocalTime.of(properties.getSnapshot().getHour(), properties.getSnapshot().getMinute());
        if (now.toLocalTime().isBefore(freezeTime)) return;
        LocalDate date = now.toLocalDate().minusDays(1);
        for (Long tenantId : tenantFrameworkService.getTenantIds()) {
            try { TenantUtils.execute(tenantId, () -> service.freeze(tenantId, date)); }
            catch (RuntimeException ex) { log.error("[freezeMediaScreenSnapshot][tenantId({})] failed", tenantId, ex); }
        }
    }
}
