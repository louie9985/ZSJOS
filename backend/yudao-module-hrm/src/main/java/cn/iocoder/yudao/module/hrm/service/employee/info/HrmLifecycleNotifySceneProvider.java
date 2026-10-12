package cn.iocoder.yudao.module.hrm.service.employee.info;

import cn.iocoder.yudao.module.system.api.notify.*;
import cn.iocoder.yudao.module.system.api.notify.dto.*;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;
import java.util.*;

@Component
public class HrmLifecycleNotifySceneProvider implements NotifySceneProvider {
    public static final String RECIPIENT = "hrm_lifecycle_recipient";
    // Scene registration happens while System builds its notification services.
    @Resource @org.springframework.context.annotation.Lazy private HrmLifecycleReminderService reminders;
    @Override public List<NotifySceneRespDTO> getScenes() {
        return List.of(scene("regular", "计划转正提醒"), scene("leave", "计划离职提醒"), scene("change", "人事异动提醒"));
    }
    private NotifySceneRespDTO scene(String kind, String title) {
        return new NotifySceneRespDTO(HrmLifecycleReminderService.SCENE + kind, title,
                List.of(new NotifySceneVariableRespDTO("employee.name", "员工姓名", false), new NotifySceneVariableRespDTO("plan.date", "计划日期", false)),
                List.of(new NotifySceneRoleRespDTO(RECIPIENT, "配置的提醒接收人")), List.of(NotifyActionType.BUSINESS_DETAIL), true);
    }
    @Override public String deliverySkipReason(NotifyBusinessEvent event) {
        return reminders.isCurrent(event) ? null : "人事计划已修改、处理、取消或接收人已失效";
    }
    @Override public Set<NotifyRecipientDTO> resolveRecipients(NotifyBusinessEvent event, Set<String> roles) {
        Object id = event.getPayload().get("recipientUserId");
        return roles.contains(RECIPIENT) && id instanceof Number n && reminders.isCurrent(event)
                ? Set.of(NotifyRecipientDTO.admin(n.longValue())) : Set.of();
    }
    @Override public Map<String, Object> resolveVariables(NotifyBusinessEvent event, NotifyRecipientDTO recipient) {
        return Map.of("employee.name", event.getPayload().get("employeeName"), "plan.date", event.getPayload().get("target"));
    }
}
