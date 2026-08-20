package cn.iocoder.yudao.module.zsjos.service.task;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.system.api.notify.NotifyRuleApi;
import cn.iocoder.yudao.module.system.api.notify.dto.NotifyTimingRuleRespDTO;
import cn.iocoder.yudao.module.system.api.dept.DeptApi;
import cn.iocoder.yudao.module.system.api.dept.dto.DeptRespDTO;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import cn.iocoder.yudao.module.system.api.user.dto.AdminUserRespDTO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.LeadDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.task.BusinessTaskDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.task.BusinessTaskNotifyStageDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.lead.LeadMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.task.BusinessTaskMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.task.BusinessTaskNotifyStageMapper;
import cn.iocoder.yudao.module.zsjos.service.lead.LeadNotifyEventPublisher;
import cn.iocoder.yudao.module.zsjos.service.studentcontact.StudentContactNotifyPublisher;
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
import static cn.iocoder.yudao.module.zsjos.service.studentcontact.StudentContactConstants.*;

@Service
public class BusinessTaskReminderService {
    private static final List<String> TASK_TYPES = List.of(
            TASK_TYPE_FIRST_FOLLOW_UP, TASK_TYPE_FOLLOW_UP_REMINDER, TASK_TYPE_QUALIFICATION,
            TYPE_FIRST_CONTACT, TYPE_STUDY_PLAN, TYPE_CONTACT);
    private static final List<String> SCENES = List.of(
            FIRST_FOLLOW_UP_REMINDER, NEXT_FOLLOW_UP_REMINDER, QUALIFICATION_REMINDER,
            NOTIFY_FIRST_CONTACT, NOTIFY_STUDY_PLAN, NOTIFY_CONTACT);

    @Resource private BusinessTaskMapper taskMapper;
    @Resource private BusinessTaskNotifyStageMapper stageMapper;
    @Resource private LeadMapper leadMapper;
    @Resource private NotifyRuleApi notifyRuleApi;
    @Resource private LeadNotifyEventPublisher notifyEventPublisher;
    @Resource private StudentContactNotifyPublisher studentNotifyPublisher;
    @Resource private AdminUserApi adminUserApi;
    @Resource private DeptApi deptApi;
    @Resource private BusinessTaskCommandService taskCommandService;

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
        Map<String, Object> context = new LinkedHashMap<>();
        context.put("reminder.stage", stageLabel(urgent.getTimingStage()));
        context.put("reminder.dueAt", task.getDueAt());
        if (BIZ_TYPE.equals(task.getBizType())) {
            Long supervisorId = currentSupervisor(task.getAssigneeId());
            context.put("plannerUserId", task.getAssigneeId());
            context.put("supervisorUserId", supervisorId);
            studentNotifyPublisher.publish(scene, task.getBizId(),
                    "student-task-reminder:" + task.getId() + ":" + urgent.getTimingStage(), urgent.getId(), now, context);
            if (TYPE_FIRST_CONTACT.equals(task.getTaskType())
                    && applicable.stream().anyMatch(rule -> "due".equals(rule.getTimingStage()))
                    && supervisorId != null) {
                createAssistanceTask(task, supervisorId);
            }
            return 1;
        }
        LeadDO lead = leadMapper.selectById(task.getBizId());
        context.put("ownerUserId", task.getAssigneeId());
        context.put("submitterUserId", lead == null ? null : lead.getSourceUserId());
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

    private String stageLabel(String stage) {
        return switch (stage) {
            case "overdue" -> "已逾期";
            case "due" -> "已到期";
            case "advance" -> "即将到期";
            default -> stage;
        };
    }

    private String sceneFor(String taskType) {
        return switch (taskType) {
            case TASK_TYPE_FIRST_FOLLOW_UP -> FIRST_FOLLOW_UP_REMINDER;
            case TASK_TYPE_QUALIFICATION -> QUALIFICATION_REMINDER;
            case TYPE_FIRST_CONTACT -> NOTIFY_FIRST_CONTACT;
            case TYPE_STUDY_PLAN -> NOTIFY_STUDY_PLAN;
            case TYPE_CONTACT -> NOTIFY_CONTACT;
            default -> NEXT_FOLLOW_UP_REMINDER;
        };
    }

    private Long currentSupervisor(Long plannerUserId) {
        AdminUserRespDTO planner = adminUserApi.getUser(plannerUserId);
        DeptRespDTO dept = planner == null || planner.getDeptId() == null ? null : deptApi.getDept(planner.getDeptId());
        return dept == null || plannerUserId.equals(dept.getLeaderUserId()) ? null : dept.getLeaderUserId();
    }

    private void createAssistanceTask(BusinessTaskDO task, Long supervisorId) {
        taskCommandService.create(new BusinessTaskCreateCommand(TYPE_ASSISTANCE, BIZ_TYPE, task.getBizId(), supervisorId,
                "协助完成学员首次联系", "请协助学习规划师完成已到期的首次联系任务", ACTION_ASSISTANCE,
                null, null, JsonUtils.toJsonString(Map.of("sourceTaskId", task.getId(),
                        "serviceRelationId", task.getBizId())), "student-assistance:" + task.getId()));
    }
}
