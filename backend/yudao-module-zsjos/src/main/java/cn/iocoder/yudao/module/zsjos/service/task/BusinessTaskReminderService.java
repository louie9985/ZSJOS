package cn.iocoder.yudao.module.zsjos.service.task;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.system.api.notify.NotifyRuleApi;
import cn.iocoder.yudao.module.system.api.notify.dto.NotifyTimingRuleRespDTO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.LeadDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.task.BusinessTaskDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.task.BusinessTaskNotifyStageDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.lead.LeadMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.task.BusinessTaskMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.task.BusinessTaskNotifyStageMapper;
import cn.iocoder.yudao.module.zsjos.service.lead.LeadNotifyEventPublisher;
import jakarta.annotation.Resource;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static cn.iocoder.yudao.module.zsjos.enums.LeadConstants.*;
import static cn.iocoder.yudao.module.zsjos.enums.LeadNotifySceneConstants.*;

@Service
public class BusinessTaskReminderService {
    private static final List<String> TASK_TYPES = List.of(
            TASK_TYPE_FIRST_FOLLOW_UP, TASK_TYPE_FOLLOW_UP_REMINDER, TASK_TYPE_QUALIFICATION);
    private static final List<String> SCENES = List.of(
            FIRST_FOLLOW_UP_REMINDER, NEXT_FOLLOW_UP_REMINDER, QUALIFICATION_REMINDER);

    @Resource private BusinessTaskMapper taskMapper;
    @Resource private BusinessTaskNotifyStageMapper stageMapper;
    @Resource private LeadMapper leadMapper;
    @Resource private NotifyRuleApi notifyRuleApi;
    @Resource private LeadNotifyEventPublisher notifyEventPublisher;

    @Transactional(rollbackFor = Exception.class)
    public int emitPending(LocalDateTime now) {
        List<NotifyTimingRuleRespDTO> rules = notifyRuleApi.getEnabledTimingRules(SCENES);
        int maximumAdvance = rules.stream().filter(rule -> "advance".equals(rule.getTimingStage()))
                .map(NotifyTimingRuleRespDTO::getTimingOffsetMinutes).max(Integer::compareTo).orElse(0);
        int emitted = 0;
        for (BusinessTaskDO candidate : taskMapper.selectPendingReminderCandidates(
                TASK_TYPES, now.plusMinutes(maximumAdvance), 200)) {
            emitted += emitForTask(candidate.getId(), now, rules);
        }
        return emitted;
    }

    @Transactional(rollbackFor = Exception.class)
    public int emitDueForTask(Long taskId, LocalDateTime now) {
        return emitForTask(taskId, now, notifyRuleApi.getEnabledTimingRules(SCENES));
    }

    private int emitForTask(Long taskId, LocalDateTime now, List<NotifyTimingRuleRespDTO> rules) {
        BusinessTaskDO task = taskMapper.selectByIdForUpdate(taskId, TenantContextHolder.getRequiredTenantId());
        if (task == null || !TASK_STATUS_PENDING.equals(task.getStatus()) || task.getDueAt() == null) return 0;
        String scene = sceneFor(task.getTaskType());
        List<NotifyTimingRuleRespDTO> applicable = rules.stream()
                .filter(rule -> scene.equals(rule.getSceneCode()))
                .filter(rule -> isDue(task.getDueAt(), rule, now))
                .filter(rule -> !stageMapper.exists(task.getId(), rule.getTimingStage()))
                .toList();
        if (applicable.isEmpty()) return 0;
        NotifyTimingRuleRespDTO urgent = applicable.stream().max(Comparator.comparingInt(
                rule -> urgency(rule.getTimingStage()))).orElseThrow();
        for (NotifyTimingRuleRespDTO rule : applicable) recordStage(task.getId(), rule, now);
        LeadDO lead = leadMapper.selectById(task.getBizId());
        Map<String, Object> context = new LinkedHashMap<>();
        context.put("ownerUserId", task.getAssigneeId());
        context.put("submitterUserId", lead == null ? null : lead.getSourceUserId());
        context.put("reminder.stage", urgent.getTimingStage());
        context.put("reminder.dueAt", task.getDueAt());
        notifyEventPublisher.publish(scene, task.getBizId(),
                "business-task-reminder:" + task.getId() + ":" + urgent.getTimingStage(), urgent.getId(),
                null, now, context);
        return 1;
    }

    private void recordStage(Long taskId, NotifyTimingRuleRespDTO rule, LocalDateTime now) {
        BusinessTaskNotifyStageDO stage = new BusinessTaskNotifyStageDO();
        stage.setTaskId(taskId); stage.setNotifyRuleId(rule.getId());
        stage.setStage(rule.getTimingStage()); stage.setEmittedAt(now);
        try { stageMapper.insert(stage); } catch (DuplicateKeyException ignored) { }
    }

    private boolean isDue(LocalDateTime dueAt, NotifyTimingRuleRespDTO rule, LocalDateTime now) {
        int offset = rule.getTimingOffsetMinutes();
        LocalDateTime emitAt = switch (rule.getTimingStage()) {
            case "advance" -> dueAt.minusMinutes(offset);
            case "overdue" -> dueAt.plusMinutes(offset);
            default -> dueAt;
        };
        return !emitAt.isAfter(now);
    }

    private int urgency(String stage) {
        return switch (stage) { case "overdue" -> 3; case "due" -> 2; default -> 1; };
    }

    private String sceneFor(String taskType) {
        return switch (taskType) {
            case TASK_TYPE_FIRST_FOLLOW_UP -> FIRST_FOLLOW_UP_REMINDER;
            case TASK_TYPE_QUALIFICATION -> QUALIFICATION_REMINDER;
            default -> NEXT_FOLLOW_UP_REMINDER;
        };
    }
}
