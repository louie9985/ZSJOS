package cn.iocoder.yudao.module.zsjos.service.export;

import cn.iocoder.yudao.framework.tenant.core.service.TenantFrameworkService;
import cn.iocoder.yudao.framework.tenant.core.util.TenantUtils;
import cn.iocoder.yudao.module.system.api.maintenance.MaintenanceModeApi;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class ExportTaskScheduler {
    @Resource private MaintenanceModeApi maintenanceModeApi;
    @Resource private TenantFrameworkService tenantFrameworkService;
    @Resource private ExportTaskService service;

    @Scheduled(fixedDelay = 2000L)
    public void process() {
        if (maintenanceModeApi.isEnabled()) return;
        for (Long tenantId : tenantFrameworkService.getTenantIds()) {
            TenantUtils.execute(tenantId, () -> {
                try {
                    service.processAvailable();
                } catch (RuntimeException error) {
                    log.error("[process][tenantId({}) 处理导出任务失败]", tenantId, error);
                }
            });
        }
    }

    @Scheduled(cron = "0 0 5 * * ?")
    public void expireFiles() {
        if (maintenanceModeApi.isEnabled()) return;
        for (Long tenantId : tenantFrameworkService.getTenantIds()) {
            TenantUtils.execute(tenantId, () -> {
                try {
                    service.expireFiles();
                    service.cleanupTerminalFiles();
                    service.cleanInactiveTasks();
                } catch (RuntimeException error) {
                    log.error("[expireFiles][tenantId({}) 清理过期导出失败]", tenantId, error);
                }
            });
        }
    }
}
