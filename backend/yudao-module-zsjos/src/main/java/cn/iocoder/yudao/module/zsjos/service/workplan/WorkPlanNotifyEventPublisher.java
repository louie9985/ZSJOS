package cn.iocoder.yudao.module.zsjos.service.workplan;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.system.api.notify.NotifyBusinessEventApi;
import cn.iocoder.yudao.module.system.api.notify.dto.NotifyBusinessEvent;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.workplan.WorkPlanDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.workplan.WorkTaskDO;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

import static cn.iocoder.yudao.module.zsjos.enums.WorkPlanConstants.BIZ_TYPE_WORK_PLAN;
import static cn.iocoder.yudao.module.zsjos.enums.WorkPlanConstants.BIZ_TYPE_WORK_TASK;

@Component
public class WorkPlanNotifyEventPublisher {
    @Resource private NotifyBusinessEventApi notifyBusinessEventApi;

    public void publishTask(String sceneCode, WorkTaskDO task, String sourceEventKey,
                            Long operatorUserId, LocalDateTime occurredAt, Map<String, Object> context) {
        Map<String, Object> payload = new LinkedHashMap<>(context == null ? Map.of() : context);
        payload.put("assigneeUserId", task.getAssigneeUserId());
        payload.put("confirmerUserId", task.getConfirmerUserId());
        payload.put("assignerUserId", task.getAssignerUserId());
        notifyBusinessEventApi.publish(NotifyBusinessEvent.builder()
                .tenantId(TenantContextHolder.getRequiredTenantId()).sceneCode(sceneCode)
                .sourceEventKey(sourceEventKey).bizType(BIZ_TYPE_WORK_TASK).bizId(task.getId())
                .operatorUserId(operatorUserId).occurredAt(occurredAt).payload(payload).build());
    }

    public void publishPlan(String sceneCode, WorkPlanDO plan, String sourceEventKey,
                            Long operatorUserId, LocalDateTime occurredAt, Map<String, Object> context) {
        Map<String, Object> payload = new LinkedHashMap<>(context == null ? Map.of() : context);
        payload.put("planOwnerUserId", plan.getOwnerUserId());
        notifyBusinessEventApi.publish(NotifyBusinessEvent.builder()
                .tenantId(TenantContextHolder.getRequiredTenantId()).sceneCode(sceneCode)
                .sourceEventKey(sourceEventKey).bizType(BIZ_TYPE_WORK_PLAN).bizId(plan.getId())
                .operatorUserId(operatorUserId).occurredAt(occurredAt).payload(payload).build());
    }
}
