package cn.iocoder.yudao.module.zsjos.service.account;

import cn.iocoder.yudao.framework.tenant.core.service.TenantFrameworkService;
import cn.iocoder.yudao.framework.tenant.core.util.TenantUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.account.MediaAccountDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.account.MediaAccountMapper;
import cn.iocoder.yudao.module.zsjos.service.task.BusinessTaskCommandService;
import cn.iocoder.yudao.module.zsjos.service.task.BusinessTaskCreateCommand;
import cn.iocoder.yudao.module.zsjos.service.delivery.DeliveryPositioningSource;
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
    @Resource private cn.iocoder.yudao.module.zsjos.dal.mysql.task.BusinessTaskMapper taskMapper;
    @Resource private cn.iocoder.yudao.module.zsjos.dal.mysql.delivery.StudentDeliveryPlanMapper plans;
    @Resource private cn.iocoder.yudao.module.zsjos.service.delivery.DeliveryPositioningSyncService sync;
    public static Map<String, Object> context(MediaAccountDO account) {
        var values = DeliveryPositioningSource.parse(account.getDetailValuesJson());
        return DeliveryPositioningSource.parse(values.get("_diagnosisContext") instanceof String json ? json : null);
    }
    public static String key(Long accountId, Object round, String type, int cycle) {
        return "media-diagnosis-v2:" + accountId + ":" + round + ":" + type + ":" + cycle;
    }

    @Scheduled(cron = "0 */5 * * * *", zone = "Asia/Shanghai")
    public void generate() {
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Shanghai"));
        for (Long tenantId : tenants.getTenantIds()) TenantUtils.execute(tenantId, () -> generateTenant(today));
    }

    void generateTenant(LocalDate today) {
        for (MediaAccountDO account : accounts.selectActiveForDiagnosis(TenantContextHolder.getRequiredTenantId())) {
            if (account.getDirectorUserId() == null) continue;
            sync.syncAccount(account.getId());
            account = accounts.selectById(account.getId());
            var ctx = context(account);
            var latestPlan = plans.selectOne(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<cn.iocoder.yudao.module.zsjos.dal.dataobject.delivery.StudentDeliveryPlanDO>()
                    .eq(cn.iocoder.yudao.module.zsjos.dal.dataobject.delivery.StudentDeliveryPlanDO::getAccountId, account.getId())
                    .orderByDesc(cn.iocoder.yudao.module.zsjos.dal.dataobject.delivery.StudentDeliveryPlanDO::getId).last("LIMIT 1"));
            boolean closed = latestPlan != null && "REPOSITIONING".equals(latestPlan.getStatus());
            for (var pending : taskMapper.selectList(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<cn.iocoder.yudao.module.zsjos.dal.dataobject.task.BusinessTaskDO>()
                    .eq(cn.iocoder.yudao.module.zsjos.dal.dataobject.task.BusinessTaskDO::getBizType, "media_account_diagnosis")
                    .eq(cn.iocoder.yudao.module.zsjos.dal.dataobject.task.BusinessTaskDO::getBizId, account.getId())
                    .eq(cn.iocoder.yudao.module.zsjos.dal.dataobject.task.BusinessTaskDO::getStatus, "pending"))) {
                var payload = DeliveryPositioningSource.parse(pending.getPayload());
                if (closed || !Objects.equals(String.valueOf(payload.get("roundKey")), String.valueOf(ctx.get("roundKey"))) || payload.get("roundKey") == null) {
                    taskMapper.update(null, new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<cn.iocoder.yudao.module.zsjos.dal.dataobject.task.BusinessTaskDO>()
                            .eq(cn.iocoder.yudao.module.zsjos.dal.dataobject.task.BusinessTaskDO::getId, pending.getId())
                            .eq(cn.iocoder.yudao.module.zsjos.dal.dataobject.task.BusinessTaskDO::getStatus, "pending")
                            .set(cn.iocoder.yudao.module.zsjos.dal.dataobject.task.BusinessTaskDO::getStatus, "cancelled")
                            .set(cn.iocoder.yudao.module.zsjos.dal.dataobject.task.BusinessTaskDO::getCancelledAt, LocalDateTime.now(ZoneId.of("Asia/Shanghai")))
                            .set(cn.iocoder.yudao.module.zsjos.dal.dataobject.task.BusinessTaskDO::getCancelReason, "诊断计时规则更新或轮次结束"));
                }
            }
            if (closed || ctx.get("anchorAt") == null) continue;
            LocalDateTime anchor = LocalDateTime.parse(String.valueOf(ctx.get("anchorAt")));
            tasks.reassignPending(List.of(TASK_TYPE_ACCOUNT_DIAGNOSIS_7D, TASK_TYPE_ACCOUNT_DIAGNOSIS_14D, TASK_TYPE_ACCOUNT_DIAGNOSIS_28D), account.getId(), account.getDirectorUserId());
            long day = java.time.temporal.ChronoUnit.DAYS.between(anchor.toLocalDate(), today);
            if (day < 0) continue;
            var values = DeliveryPositioningSource.parse(account.getDetailValuesJson());
            for (var spec : List.of(new Object[]{7, TASK_TYPE_ACCOUNT_DIAGNOSIS_7D, "7天账号数据诊断"}, new Object[]{14, TASK_TYPE_ACCOUNT_DIAGNOSIS_14D, "14天验证指标诊断"}, new Object[]{28, TASK_TYPE_ACCOUNT_DIAGNOSIS_28D, "28天调整触发条件"})) {
                int interval = (Integer) spec[0];
                // Open the current period before its deadline; retain missed periods for catch-up.
                int cycle = (int) day / interval + 1;
                String type = (String) spec[1];
                for (int completedCycle = 1; completedCycle <= cycle; completedCycle++) {
                    LocalDateTime due = anchor.toLocalDate().plusDays((long) interval * completedCycle).atStartOfDay();
                    var payload = new LinkedHashMap<String,Object>();
                    String template = "diagnosis_" + interval + "d";
                    payload.put("accountId", account.getId()); payload.put("templateType", template);
                    payload.put("cycle", completedCycle); payload.put("roundKey", ctx.get("roundKey"));
                    payload.put("sourceSubmissionId", ctx.get("submissionId")); payload.put("source", ctx);
                    var requirements = new LinkedHashMap<String,Object>();
                    for (int days : List.of(7,14,28)) requirements.put("diagnosis_" + days + "d", values.get("diagnosis_" + days + "d_requirement"));
                    payload.put("requirementSnapshot", requirements);
                    tasks.create(new BusinessTaskCreateCommand(type, "media_account_diagnosis", account.getId(), account.getDirectorUserId(),
                            (String) spec[2], "请填写账号周期诊断表单", "MEDIA_ACCOUNT_DIAGNOSIS", due, due,
                            cn.iocoder.yudao.framework.common.util.json.JsonUtils.toJsonString(payload), key(account.getId(), ctx.get("roundKey"), type, completedCycle)));
                }
            }
        }
    }

}
