package cn.iocoder.yudao.module.zsjos.job.studentdelivery;

import cn.iocoder.yudao.framework.tenant.core.util.TenantUtils;
import cn.iocoder.yudao.module.zsjos.service.deliveryclass.ExceptionDeliveryClassService;
import jakarta.annotation.Resource;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import lombok.extern.slf4j.Slf4j;

@Component @Slf4j
public class ExceptionDeliveryClassInitializer {
    @Resource private ExceptionDeliveryClassService classes;
    @Resource private cn.iocoder.yudao.module.system.api.maintenance.MaintenanceModeApi maintenance;
    @EventListener(ApplicationReadyEvent.class)
    public void initialize() { reconcile(); }
    @Scheduled(fixedDelay=60_000L, initialDelay=60_000L)
    public void reconcile() {
        if (maintenance.isEnabled()) return;
        TenantUtils.execute(1L, () -> {
            try { classes.reconcile(); }
            catch (RuntimeException failure) { log.error("[reconcile][exception class initialization failed]",failure); }
        });
    }
}
