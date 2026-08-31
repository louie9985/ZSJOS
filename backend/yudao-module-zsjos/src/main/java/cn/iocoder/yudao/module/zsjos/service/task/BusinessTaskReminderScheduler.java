package cn.iocoder.yudao.module.zsjos.service.task;

import cn.iocoder.yudao.framework.tenant.core.service.TenantFrameworkService;
import cn.iocoder.yudao.module.system.api.maintenance.MaintenanceModeApi;
import cn.iocoder.yudao.framework.tenant.core.util.TenantUtils;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
@Slf4j
public class BusinessTaskReminderScheduler {
    @Resource private TenantFrameworkService tenantFrameworkService;
    @Resource private BusinessTaskReminderService reminderService;
    @Resource private MaintenanceModeApi maintenanceModeApi;

    @Scheduled(fixedDelay = 60_000L)
    public void emitReminders() {
        if (maintenanceModeApi.isEnabled()) return;
        for (Long tenantId : tenantFrameworkService.getTenantIds()) {
            TenantUtils.execute(tenantId, () -> {
                try { reminderService.emitPending(LocalDateTime.now()); }
                catch (RuntimeException ex) {
                    log.error("[emitReminders][tenantId({}) 处理业务提醒失败]", tenantId, ex);
                }
            });
        }
    }
}
