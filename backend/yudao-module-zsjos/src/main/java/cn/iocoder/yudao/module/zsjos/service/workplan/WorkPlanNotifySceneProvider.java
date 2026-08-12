package cn.iocoder.yudao.module.zsjos.service.workplan;

import cn.iocoder.yudao.module.system.api.notify.NotifyActionType;
import cn.iocoder.yudao.module.system.api.notify.NotifySceneProvider;
import cn.iocoder.yudao.module.system.api.notify.dto.*;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.workplan.WorkPlanDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.workplan.WorkTaskDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.workplan.WorkPlanMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.workplan.WorkTaskMapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

import java.util.*;

import static cn.iocoder.yudao.module.zsjos.enums.WorkPlanNotifySceneConstants.*;

@Component
public class WorkPlanNotifySceneProvider implements NotifySceneProvider {
    @Resource private WorkTaskMapper taskMapper;
    @Resource private WorkPlanMapper planMapper;

    @Override
    public List<NotifySceneRespDTO> getScenes() {
        return List.of(scene(SCENE_ASSIGNED, "工作任务分派", ROLE_ASSIGNEE),
                scene(SCENE_REMINDER, "工作任务提醒", ROLE_ASSIGNEE), scene(SCENE_OVERDUE, "工作任务首次逾期", ROLE_ASSIGNEE),
                scene(SCENE_REPORT_SUBMITTED, "完成汇报待确认", ROLE_CONFIRMER),
                scene(SCENE_CONFIRM_APPROVED, "任务确认完成", ROLE_ASSIGNEE, ROLE_ASSIGNER),
                scene(SCENE_CONFIRM_REJECTED, "完成汇报退回", ROLE_ASSIGNEE, ROLE_ASSIGNER),
                scene(SCENE_ADJUSTED, "工作任务调整", ROLE_ASSIGNEE, ROLE_CONFIRMER),
                scene(SCENE_CANCELLED, "工作任务取消", ROLE_ASSIGNEE, ROLE_CONFIRMER),
                scene(SCENE_SUMMARY_READY, "计划待总结", ROLE_PLAN_OWNER));
    }

    @Override
    public Set<Long> resolveRecipients(NotifyBusinessEvent event, Set<String> roles) {
        Map<String, Object> payload = event.getPayload() == null ? Map.of() : event.getPayload();
        Set<Long> result = new LinkedHashSet<>();
        for (String role : roles) {
            Object raw = payload.get(switch (role) {
                case ROLE_ASSIGNEE -> "assigneeUserId";
                case ROLE_CONFIRMER -> "confirmerUserId";
                case ROLE_ASSIGNER -> "assignerUserId";
                case ROLE_PLAN_OWNER -> "planOwnerUserId";
                default -> "";
            });
            if (raw instanceof Number number && number.longValue() > 0) result.add(number.longValue());
        }
        return result;
    }

    @Override
    public Map<String, Object> resolveVariables(NotifyBusinessEvent event, Long recipientUserId) {
        Map<String, Object> values = new LinkedHashMap<>();
        WorkTaskDO task = taskMapper.selectById(event.getBizId());
        if (task != null) {
            values.put("task.id", task.getId()); values.put("task.title", task.getTitle()); values.put("task.status", task.getStatus());
            values.put("task.dueAt", task.getDueAt()); values.put("task.remindAt", task.getRemindAt());
        } else {
            WorkPlanDO plan = planMapper.selectById(event.getBizId());
            if (plan != null) { values.put("plan.id", plan.getId()); values.put("plan.title", plan.getTitle()); values.put("plan.status", plan.getStatus()); }
        }
        values.put("event.time", event.getOccurredAt());
        if (event.getPayload() != null) values.putAll(event.getPayload());
        return values;
    }

    private NotifySceneRespDTO scene(String code, String name, String... roles) {
        List<NotifySceneVariableRespDTO> variables = List.of(
                new NotifySceneVariableRespDTO("task.id", "任务编号", false), new NotifySceneVariableRespDTO("task.title", "任务名称", false),
                new NotifySceneVariableRespDTO("task.status", "任务状态", false), new NotifySceneVariableRespDTO("task.dueAt", "截止时间", false),
                new NotifySceneVariableRespDTO("task.remindAt", "提醒时间", false), new NotifySceneVariableRespDTO("plan.id", "计划编号", false),
                new NotifySceneVariableRespDTO("plan.title", "计划名称", false), new NotifySceneVariableRespDTO("plan.status", "计划状态", false),
                new NotifySceneVariableRespDTO("event.time", "事件时间", false), new NotifySceneVariableRespDTO("reason", "调整或退回原因", false));
        return new NotifySceneRespDTO(code, name, variables, Arrays.stream(roles)
                .map(role -> new NotifySceneRoleRespDTO(role, roleLabel(role))).toList(),
                List.of(NotifyActionType.NONE, NotifyActionType.MESSAGE_DETAIL, NotifyActionType.BUSINESS_DETAIL));
    }

    private String roleLabel(String role) {
        return switch (role) {
            case ROLE_ASSIGNEE -> "责任人"; case ROLE_CONFIRMER -> "确认人"; case ROLE_ASSIGNER -> "分派人"; case ROLE_PLAN_OWNER -> "计划负责人";
            default -> role;
        };
    }
}
