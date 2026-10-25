package cn.iocoder.yudao.module.zsjos.job.studentdelivery;

import cn.iocoder.yudao.framework.tenant.core.util.TenantUtils;
import cn.iocoder.yudao.module.system.api.maintenance.MaintenanceModeApi;
import cn.iocoder.yudao.module.zsjos.service.deliveryclass.UnscheduledDeliveryClassService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class UnscheduledDeliveryClassInitializer {
    @Resource private UnscheduledDeliveryClassService classes;
    @Resource private MaintenanceModeApi maintenance;

    @EventListener(ApplicationReadyEvent.class)
    public void initialize() { reconcile(); }

    @Scheduled(fixedDelay = 60_000L, initialDelay = 60_000L)
    public void reconcile() {
        if (maintenance.isEnabled()) return;
        TenantUtils.execute(1L, () -> {
            try { classes.reconcile(); }
            catch (RuntimeException failure) { log.error("[reconcile][unscheduled class initialization failed]", failure); }
        });
    }
}
