package cn.iocoder.yudao.module.zsjos.service.lead;

import cn.iocoder.yudao.framework.tenant.core.service.TenantFrameworkService;
import cn.iocoder.yudao.module.system.api.maintenance.MaintenanceModeApi;
import cn.iocoder.yudao.framework.tenant.core.util.TenantUtils;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class LeadQualificationTimeoutScheduler {
    @Resource private TenantFrameworkService tenantFrameworkService;
    @Resource private LeadQualificationService qualificationService;
    @Resource private MaintenanceModeApi maintenanceModeApi;

    @Scheduled(fixedDelay = 60_000L)
    public void processExpiredQualifications() {
        if (maintenanceModeApi.isEnabled()) return;
        for (Long tenantId : tenantFrameworkService.getTenantIds()) {
            TenantUtils.execute(tenantId, () -> {
                try {
                    qualificationService.processExpired();
                } catch (RuntimeException ex) {
                    log.error("[processExpiredQualifications][tenantId({}) 处理有效性判定超时失败]", tenantId, ex);
                }
            });
        }
    }
}
