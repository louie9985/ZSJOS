package cn.iocoder.yudao.module.zsjos.service.lead;

import cn.iocoder.yudao.framework.tenant.core.service.TenantFrameworkService;
import cn.iocoder.yudao.framework.tenant.core.util.TenantUtils;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class LeadAssignmentTimeoutScheduler {
    @Resource private TenantFrameworkService tenantFrameworkService;
    @Resource private LeadDispatchService dispatchService;

    @Scheduled(fixedDelay = 5000L)
    public void processExpiredAssignments() {
        for (Long tenantId : tenantFrameworkService.getTenantIds()) {
            TenantUtils.execute(tenantId, () -> {
                try {
                    dispatchService.processExpired();
                    dispatchService.processUnassignedRetries();
                } catch (RuntimeException ex) {
                    log.error("[processExpiredAssignments][tenantId({}) 处理客资派单超时失败]", tenantId, ex);
                }
            });
        }
    }
}
