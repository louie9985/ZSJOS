package cn.iocoder.yudao.module.zsjos.service.forcedform;

import cn.iocoder.yudao.framework.tenant.core.service.TenantFrameworkService;
import cn.iocoder.yudao.framework.tenant.core.util.TenantUtils;
import jakarta.annotation.Resource;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class ForcedFormTemporaryFileCleanupScheduler {

    @Resource private TenantFrameworkService tenantFrameworkService;
    @Resource private ForcedFormService service;

    @Scheduled(cron = "${zsjos.forced-form.temporary-cleanup-cron:0 0/30 * * * ?}")
    public void cleanup() {
        for (Long tenantId : tenantFrameworkService.getTenantIds()) {
            TenantUtils.execute(tenantId, service::cleanupTemporaryFiles);
        }
    }
}
