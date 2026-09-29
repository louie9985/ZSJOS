package cn.iocoder.yudao.module.zsjos.service.calendar;

import cn.iocoder.yudao.framework.tenant.core.service.TenantFrameworkService;
import cn.iocoder.yudao.framework.tenant.core.util.TenantUtils;
import cn.iocoder.yudao.module.system.api.maintenance.MaintenanceModeApi;
import cn.iocoder.yudao.module.zsjos.dal.mysql.calendar.CalendarNotifyIntentMapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import java.time.LocalDateTime;

@Component
@Slf4j
public class CalendarNotificationIntentDispatcher {
    @Resource private CalendarNotificationIntentRecoveryService recovery;
    @Resource private CalendarNotifyIntentMapper mapper;
    @Resource private TenantFrameworkService tenants;
    @Resource private MaintenanceModeApi maintenance;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void afterCommit(CalendarNotificationIntentService.Ready ready) {
        try {
            if (maintenance.isEnabled()) return;
            tenants.validTenant(ready.tenantId());
            TenantUtils.execute(ready.tenantId(), () -> attempt(ready.intentId()));
        } catch (RuntimeException failure) {
            // The durable intent remains pending; never turn a committed maintenance response into an error.
            log.warn("Calendar notification intent {} deferred after commit", ready.intentId());
        }
    }

    @Scheduled(fixedDelayString = "${zsjos.calendar-notification.recovery-delay:30000}")
    public void recoverPending() {
        if (maintenance.isEnabled()) return;
        for (Long tenantId : tenants.getTenantIds()) {
            try {
                tenants.validTenant(tenantId);
                TenantUtils.execute(tenantId, () -> {
                    for (Long id : mapper.selectDue(tenantId, LocalDateTime.now())) attempt(id);
                });
            } catch (RuntimeException failure) {
                log.warn("Calendar notification recovery deferred for tenant {}", tenantId);
            }
        }
    }

    private void attempt(Long id) {
        try { recovery.recover(id); }
        catch (RuntimeException failure) {
            try { recovery.recordFailure(id, failure); }
            catch (RuntimeException recordingFailure) {
                log.warn("Calendar notification intent {} remains pending after recovery failure", id);
            }
        }
    }
}
