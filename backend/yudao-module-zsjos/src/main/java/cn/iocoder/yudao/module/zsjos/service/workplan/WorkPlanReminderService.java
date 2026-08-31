package cn.iocoder.yudao.module.zsjos.service.workplan;

import cn.iocoder.yudao.module.zsjos.dal.dataobject.workplan.WorkTaskDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.workplan.WorkTaskMapper;
import cn.iocoder.yudao.framework.tenant.core.service.TenantFrameworkService;
import cn.iocoder.yudao.module.system.api.maintenance.MaintenanceModeApi;
import cn.iocoder.yudao.framework.tenant.core.util.TenantUtils;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Map;

import static cn.iocoder.yudao.module.zsjos.enums.WorkPlanNotifySceneConstants.SCENE_OVERDUE;
import static cn.iocoder.yudao.module.zsjos.enums.WorkPlanNotifySceneConstants.SCENE_REMINDER;

@Service
@Slf4j
public class WorkPlanReminderService {
    private static final int BATCH_SIZE = 200;
    @Resource private WorkTaskMapper taskMapper;
    @Resource private WorkPlanNotifyEventPublisher notifyPublisher;
    @Resource private TenantFrameworkService tenantFrameworkService;
    @Resource private MaintenanceModeApi maintenanceModeApi;
    @Resource @Lazy private WorkPlanReminderService self;

    @Scheduled(fixedDelayString = "${zsjos.work-plan.reminder-scan-delay:60000}")
    public void scanAllTenants() {
        if (maintenanceModeApi.isEnabled()) return;
        for (Long tenantId : tenantFrameworkService.getTenantIds()) {
            TenantUtils.execute(tenantId, () -> {
                try {
                    self.scan();
                } catch (RuntimeException ex) {
                    log.error("[scanAllTenants][tenantId({}) 扫描工作计划提醒失败]", tenantId, ex);
                }
            });
        }
    }

    @cn.iocoder.yudao.module.zsjos.framework.audit.ZsjosAudit(action = "work-plan.scan-reminders", targetType = "work-task")
    @Transactional
    public void scan() {
        LocalDateTime now = LocalDateTime.now();
        for (WorkTaskDO task : taskMapper.selectReminderCandidates(now, BATCH_SIZE)) {
            if (taskMapper.markReminderNotified(task.getId(), now) == 1) {
                notifyPublisher.publishTask(SCENE_REMINDER, task, "work-task-reminder:" + task.getId(), 0L, now, Map.of());
            }
        }
        for (WorkTaskDO task : taskMapper.selectOverdueCandidates(now, BATCH_SIZE)) {
            if (taskMapper.markOverdueNotified(task.getId(), now) == 1) {
                notifyPublisher.publishTask(SCENE_OVERDUE, task, "work-task-overdue:" + task.getId(), 0L, now, Map.of());
            }
        }
    }
}
