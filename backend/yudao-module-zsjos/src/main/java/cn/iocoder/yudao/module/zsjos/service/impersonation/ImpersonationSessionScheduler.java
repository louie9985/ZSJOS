package cn.iocoder.yudao.module.zsjos.service.impersonation;

import cn.iocoder.yudao.framework.tenant.core.service.TenantFrameworkService;
import cn.iocoder.yudao.framework.tenant.core.util.TenantUtils;
import cn.iocoder.yudao.module.system.api.maintenance.MaintenanceModeApi;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class ImpersonationSessionScheduler {
    @Resource private MaintenanceModeApi maintenanceModeApi;
    @Resource private TenantFrameworkService tenantFrameworkService;
    @Resource private ImpersonationService service;

    @Scheduled(cron = "0 * * * * ?")
    public void expireIdleSessions() {
        if (maintenanceModeApi.isEnabled()) return;
        for (Long tenantId : tenantFrameworkService.getTenantIds()) {
            TenantUtils.execute(tenantId, () -> {
                try {
                    service.expireIdleSessions();
                } catch (RuntimeException ex) {
                    log.error("[expireIdleSessions][tenantId({}) 清理冒充会话失败]", tenantId, ex);
                }
            });
        }
    }
}
