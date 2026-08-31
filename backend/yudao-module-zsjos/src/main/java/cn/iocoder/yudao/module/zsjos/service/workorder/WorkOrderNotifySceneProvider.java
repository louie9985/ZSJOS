package cn.iocoder.yudao.module.zsjos.service.workorder;

import cn.iocoder.yudao.module.system.api.notify.NotifyActionType;
import cn.iocoder.yudao.module.system.api.notify.NotifySceneProvider;
import cn.iocoder.yudao.module.system.api.notify.dto.*;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
public class WorkOrderNotifySceneProvider implements NotifySceneProvider {
    public static final String ASSIGNED = "zsjos.work_order.assigned";
    public static final String POOL_AVAILABLE = "zsjos.work_order.pool_available";
    public static final String TAKEN = "zsjos.work_order.taken";
    public static final String REJECTED = "zsjos.work_order.rejected";
    public static final String REVIEW_REQUESTED = "zsjos.work_order.review_requested";
    public static final String REWORKED = "zsjos.work_order.reworked";
    public static final String COMPLETED = "zsjos.work_order.completed";
    public static final String TERMINATED = "zsjos.work_order.terminated";
    public static final String WITHDRAWN = "zsjos.work_order.withdrawn";

    @Override
    public List<NotifySceneRespDTO> getScenes() {
        return List.of(scene(ASSIGNED, "收到指定工单"), scene(POOL_AVAILABLE, "候选池有新工单"),
                scene(TAKEN, "工单已接单"), scene(REJECTED, "工单被拒绝"),
                scene(REVIEW_REQUESTED, "工单待验收"), scene(REWORKED, "工单已打回重做"),
                scene(COMPLETED, "工单已验收通过"), scene(TERMINATED, "工单不合格终止"),
                scene(WITHDRAWN, "工单已撤回"));
    }

    @Override
    public Set<NotifyRecipientDTO> resolveRecipients(NotifyBusinessEvent event, Set<String> roles) {
        Set<NotifyRecipientDTO> result = new LinkedHashSet<>();
        Object recipients = event.getPayload() == null ? null : event.getPayload().get("recipientUserIds");
        if (recipients instanceof Collection<?> ids) ids.forEach(id -> add(result, id));
        add(result, event.getPayload() == null ? null : event.getPayload().get("recipientUserId"));
        return result;
    }

    @Override
    public Map<String, Object> resolveVariables(NotifyBusinessEvent event, NotifyRecipientDTO recipient) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("workOrder.id", event.getBizId()); result.put("event.time", event.getOccurredAt());
        if (event.getPayload() != null) result.putAll(event.getPayload());
        return result;
    }

    private NotifySceneRespDTO scene(String code, String name) {
        return new NotifySceneRespDTO(code, name, List.of(
                new NotifySceneVariableRespDTO("workOrder.id", "工单内部编号", false),
                new NotifySceneVariableRespDTO("orderNo", "工单编号", false),
                new NotifySceneVariableRespDTO("sceneName", "工单类型", false),
                new NotifySceneVariableRespDTO("deepLink", "工单详情链接", false),
                new NotifySceneVariableRespDTO("event.time", "发生时间", false)),
                List.of(new NotifySceneRoleRespDTO("recipient", "工单相关人员")),
                List.of(NotifyActionType.BUSINESS_DETAIL), false);
    }

    private void add(Set<NotifyRecipientDTO> recipients, Object value) {
        if (value instanceof Number number && number.longValue() > 0) recipients.add(NotifyRecipientDTO.admin(number.longValue()));
    }
}
