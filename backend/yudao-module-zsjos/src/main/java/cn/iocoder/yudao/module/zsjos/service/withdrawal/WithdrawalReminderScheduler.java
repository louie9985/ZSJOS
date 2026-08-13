package cn.iocoder.yudao.module.zsjos.service.withdrawal;

import cn.iocoder.yudao.framework.tenant.core.service.TenantFrameworkService;
import cn.iocoder.yudao.framework.tenant.core.util.TenantUtils;
import cn.iocoder.yudao.module.system.api.maintenance.MaintenanceModeApi;
import jakarta.annotation.Resource;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class WithdrawalReminderScheduler {
    @Resource private MaintenanceModeApi maintenanceModeApi;
    @Resource private TenantFrameworkService tenantFrameworkService;
    @Resource private WithdrawalServiceImpl service;
    @Scheduled(cron = "${zsjos.withdrawal.reminder-cron:0 30 10 ? * THU}")
    public void remind() {
        if (maintenanceModeApi.isEnabled()) return;
        for (Long tenantId : tenantFrameworkService.getTenantIds()) TenantUtils.execute(tenantId, service::sendFinanceReminder);
    }
}
