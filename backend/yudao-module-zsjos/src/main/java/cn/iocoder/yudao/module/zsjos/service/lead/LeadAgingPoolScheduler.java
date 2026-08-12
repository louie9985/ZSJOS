package cn.iocoder.yudao.module.zsjos.service.lead;

import cn.iocoder.yudao.framework.tenant.core.service.TenantFrameworkService;
import cn.iocoder.yudao.framework.tenant.core.util.TenantUtils;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
@Slf4j
public class LeadAgingPoolScheduler {
    @Resource private TenantFrameworkService tenantFrameworkService;
    @Resource private LeadAgingPoolService service;
    @Scheduled(fixedDelay = 60_000L)
    public void process() {
        for (Long tenantId : tenantFrameworkService.getTenantIds()) TenantUtils.execute(tenantId, () -> {
            LocalDateTime now = LocalDateTime.now();
            runStep(tenantId, "scanDue", () -> service.scanDue(now));
            runStep(tenantId, "clearInvalidCollaborators", () -> service.clearInvalidCollaborators(now));
            runStep(tenantId, "emitAdvanceReminders", () -> service.emitAdvanceReminders(now));
        });
    }
    private void runStep(Long tenantId, String step, Runnable action) {
        try { action.run(); }
        catch (RuntimeException ex) { log.error("[processAgingPool][tenantId({}) step({}) 处理失败]", tenantId, step, ex); }
    }
}
