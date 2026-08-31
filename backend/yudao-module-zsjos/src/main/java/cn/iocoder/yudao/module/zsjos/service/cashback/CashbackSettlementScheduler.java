package cn.iocoder.yudao.module.zsjos.service.cashback;

import cn.iocoder.yudao.framework.tenant.core.service.TenantFrameworkService;
import cn.iocoder.yudao.framework.tenant.core.util.TenantUtils;
import cn.iocoder.yudao.module.system.api.maintenance.MaintenanceModeApi;
import jakarta.annotation.Resource;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class CashbackSettlementScheduler {
    @Resource private MaintenanceModeApi maintenanceModeApi;
    @Resource private TenantFrameworkService tenantFrameworkService;
    @Resource private CashbackService service;

    @Scheduled(fixedDelayString = "${zsjos.cashback.settlement-scan-delay:3600000}")
    public void settle() {
        if (maintenanceModeApi.isEnabled()) return;
        for (Long tenantId : tenantFrameworkService.getTenantIds()) {
            TenantUtils.execute(tenantId, service::settleMatured);
        }
    }
}
