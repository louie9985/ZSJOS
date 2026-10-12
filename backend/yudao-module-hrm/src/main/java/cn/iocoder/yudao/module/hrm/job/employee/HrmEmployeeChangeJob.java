package cn.iocoder.yudao.module.hrm.job.employee;

import cn.iocoder.yudao.framework.quartz.core.handler.JobHandler;
import cn.iocoder.yudao.framework.tenant.core.job.TenantJob;
import cn.iocoder.yudao.module.hrm.service.employee.info.HrmLifecycleReminderService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

/** Kept under the original Quartz handler name for deployed scheduler compatibility. */
@Component
public class HrmEmployeeChangeJob implements JobHandler {
    @Resource private HrmLifecycleReminderService reminders;
    @Override @TenantJob
    public String execute(String param) {
        return reminders.dispatch();
    }
}
