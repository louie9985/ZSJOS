package cn.iocoder.yudao.module.zsjos.service.impersonation;

import cn.iocoder.yudao.module.system.api.maintenance.MaintenanceModeApi;
import jakarta.annotation.Resource;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class ImpersonationSessionScheduler {
    @Resource private MaintenanceModeApi maintenanceModeApi;
    @Resource private ImpersonationService service;

    @Scheduled(cron = "0 * * * * ?")
    public void expireIdleSessions() {
        if (maintenanceModeApi.isEnabled()) return;
        service.expireIdleSessions();
    }
}
