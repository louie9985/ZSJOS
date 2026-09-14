package cn.iocoder.yudao.module.zsjos.service.account;

import cn.iocoder.yudao.framework.tenant.core.service.TenantFrameworkService;
import cn.iocoder.yudao.framework.tenant.core.util.TenantUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.account.MediaAccountDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.account.MediaAccountMapper;
import cn.iocoder.yudao.module.zsjos.service.task.BusinessTaskCommandService;
import cn.iocoder.yudao.module.zsjos.service.task.BusinessTaskCreateCommand;
import jakarta.annotation.Resource;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import java.time.*;
import java.util.*;
import static cn.iocoder.yudao.module.zsjos.enums.LeadConstants.*;

/** Creates the independent recurring diagnosis tasks. The idempotency key is the account/cycle/template tuple. */
@Component
public class MediaAccountDiagnosisScheduler {
    @Resource private TenantFrameworkService tenants;
    @Resource private MediaAccountMapper accounts;
    @Resource private BusinessTaskCommandService tasks;

    @Scheduled(cron = "0 5 0 * * *")
    public void generate() {
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Shanghai"));
        for (Long tenantId : tenants.getTenantIds()) TenantUtils.execute(tenantId, () -> generateTenant(today));
    }

    void generateTenant(LocalDate today) {
        for (MediaAccountDO account : accounts.selectActiveForDiagnosis(TenantContextHolder.getRequiredTenantId())) {
            if (account.getCreateTime() == null || account.getDirectorUserId() == null) continue;
            tasks.reassignPending(List.of(TASK_TYPE_ACCOUNT_DIAGNOSIS_7D, TASK_TYPE_ACCOUNT_DIAGNOSIS_14D, TASK_TYPE_ACCOUNT_DIAGNOSIS_28D), account.getId(), account.getDirectorUserId());
            long day = effectiveDay(account, today);
            for (var spec : List.of(new Object[]{7, TASK_TYPE_ACCOUNT_DIAGNOSIS_7D, "7天账号数据诊断"}, new Object[]{14, TASK_TYPE_ACCOUNT_DIAGNOSIS_14D, "14天验证指标诊断"}, new Object[]{28, TASK_TYPE_ACCOUNT_DIAGNOSIS_28D, "28天调整触发条件"})) {
                int interval = (Integer) spec[0];
                int cycle = (int) day / interval;
                if (cycle <= 0) continue;
                String type = (String) spec[1];
                for (int completedCycle = 1; completedCycle <= cycle; completedCycle++) {
                    LocalDateTime due = today.plusDays(1).atStartOfDay().minusNanos(1);
                    tasks.create(new BusinessTaskCreateCommand(type, "media_account_diagnosis", account.getId(), account.getDirectorUserId(),
                            (String) spec[2], "请填写账号周期诊断表单", "MEDIA_ACCOUNT_DIAGNOSIS", due, due,
                            "{\"templateType\":\"" + type + "\",\"cycle\":" + completedCycle + "}", "media-diagnosis:" + account.getId() + ":" + type + ":" + completedCycle));
                }
            }
        }
    }

    static long effectiveDay(MediaAccountDO account, LocalDate today) {
        LocalDate anchor = account.getCreateTime().toLocalDate();
        long paused = 0;
        if (account.getMaintenanceStartDate() != null && account.getMaintenanceEndDate() != null
                && !account.getMaintenanceEndDate().isBefore(account.getMaintenanceStartDate())) {
            LocalDate end = today.isBefore(account.getMaintenanceEndDate()) ? today : account.getMaintenanceEndDate();
            if (!end.isBefore(account.getMaintenanceStartDate())) paused = Duration.between(account.getMaintenanceStartDate().atStartOfDay(), end.plusDays(1).atStartOfDay()).toDays();
        }
        return Duration.between(anchor.atStartOfDay(), today.atStartOfDay()).toDays() + 1 - paused;
    }
}
