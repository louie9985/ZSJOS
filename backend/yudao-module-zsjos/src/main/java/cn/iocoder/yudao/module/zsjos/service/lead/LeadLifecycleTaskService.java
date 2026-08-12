package cn.iocoder.yudao.module.zsjos.service.lead;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.event.BusinessEventDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.LeadFollowUpRuleDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.LeadDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.event.BusinessEventMapper;
import cn.iocoder.yudao.module.zsjos.service.task.BusinessTaskCommandService;
import cn.iocoder.yudao.module.zsjos.service.task.BusinessTaskCreateCommand;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Map;

import static cn.iocoder.yudao.module.zsjos.enums.LeadConstants.*;

@Service
public class LeadLifecycleTaskService {
    @Resource private BusinessTaskCommandService taskCommandService;
    @Resource private BusinessEventMapper eventMapper;
    @Resource private LeadFollowUpRuleService followUpRuleService;

    public void createAssignmentTask(Long leadId, Long assigneeId, Long assignmentHistoryId,
                                     LocalDateTime dueAt, String dispatchMode) {
        taskCommandService.create(command(TASK_TYPE_ASSIGNMENT_ACCEPT, leadId, assigneeId,
                "待接客资：客资 #" + leadId, "OPEN_LEAD_ASSIGNMENT", dueAt,
                JsonUtils.toJsonString(Map.of("assignmentHistoryId", assignmentHistoryId,
                        "dispatchMode", dispatchMode)), "lead-assignment-accept:" + assignmentHistoryId));
    }

    public void completeAssignmentTask(Long leadId, Long assigneeId, LocalDateTime completedAt) {
        taskCommandService.complete(TASK_TYPE_ASSIGNMENT_ACCEPT, leadId, assigneeId, completedAt);
    }

    public void cancelAssignmentTask(Long leadId, Long assigneeId, LocalDateTime cancelledAt, String reason) {
        taskCommandService.cancel(TASK_TYPE_ASSIGNMENT_ACCEPT, leadId, assigneeId, cancelledAt, reason);
    }

    public LocalDateTime createFirstFollowUpTask(Long leadId, Long assigneeId, Long assignmentHistoryId,
                                        LocalDateTime ownershipStartedAt, String eventType,
                                        String fromAssignmentStatus) {
        LeadFollowUpRuleDO rule = followUpRuleService.requireEnabledRule();
        int timeoutMinutes = rule.getFirstFollowUpTimeoutMinutes();
        LocalDateTime dueAt = ownershipStartedAt.plusMinutes(timeoutMinutes);
        taskCommandService.create(command(TASK_TYPE_FIRST_FOLLOW_UP, leadId, assigneeId,
                "首次跟进：客资 #" + leadId, "OPEN_LEAD_FOLLOW_UP", dueAt,
                JsonUtils.toJsonString(Map.of("assignmentHistoryId", assignmentHistoryId,
                        "ruleId", rule.getId(), "ruleVersion", rule.getVersion() == null ? 0 : rule.getVersion(),
                        "timeoutMinutes", timeoutMinutes, "ownershipStartedAt", ownershipStartedAt.toString())),
                "lead-first-follow-up:" + assignmentHistoryId));
        addOwnershipEvent(leadId, assigneeId, assignmentHistoryId, ownershipStartedAt,
                eventType, fromAssignmentStatus);
        return dueAt;
    }

    public void cancelFirstFollowUpTasks(Long leadId, LocalDateTime cancelledAt, String reason) {
        taskCommandService.cancel(TASK_TYPE_FIRST_FOLLOW_UP, leadId, null, cancelledAt, reason);
    }

    public boolean completeFirstFollowUpTask(Long assignmentHistoryId, LocalDateTime completedAt) {
        return taskCommandService.completeByKey("lead-first-follow-up:" + assignmentHistoryId, completedAt);
    }

    public void replaceFollowUpReminder(Long leadId, Long assigneeId, Long recordId,
                                        LocalDateTime dueAt, LocalDateTime changedAt) {
        taskCommandService.complete(TASK_TYPE_FOLLOW_UP_REMINDER, leadId, assigneeId, changedAt);
        if (dueAt == null) return;
        taskCommandService.create(command(TASK_TYPE_FOLLOW_UP_REMINDER, leadId, assigneeId,
                "跟进提醒：客资 #" + leadId, "OPEN_LEAD_FOLLOW_UP", dueAt,
                JsonUtils.toJsonString(Map.of("followUpRecordId", recordId)),
                "lead-follow-up-reminder:" + recordId));
    }

    public void cancelFollowUpReminders(Long leadId, LocalDateTime cancelledAt, String reason) {
        taskCommandService.cancel(TASK_TYPE_FOLLOW_UP_REMINDER, leadId, null, cancelledAt, reason);
    }

    public LeadFollowUpRuleDO createQualificationTask(LeadDO lead, Long assigneeId,
                                                       LocalDateTime startedAt) {
        LeadFollowUpRuleDO rule = followUpRuleService.requireEnabledRule();
        int roundNo = (lead.getQualificationRoundNo() == null ? 0 : lead.getQualificationRoundNo()) + 1;
        int timeoutMinutes = rule.getQualificationTimeoutMinutes();
        LocalDateTime dueAt = startedAt.plusMinutes(timeoutMinutes);
        lead.setQualificationRoundNo(roundNo);
        lead.setQualificationStartedAt(startedAt);
        lead.setQualificationDeadlineAt(dueAt);
        lead.setQualificationRuleSnapshot(JsonUtils.toJsonString(Map.of(
                "ruleId", rule.getId(),
                "ruleVersion", rule.getVersion() == null ? 0 : rule.getVersion(),
                "timeoutMinutes", timeoutMinutes,
                "startedAt", startedAt.toString())));
        lead.setSuspendedAt(null);

        taskCommandService.create(command(TASK_TYPE_QUALIFICATION, lead.getId(), assigneeId,
                "有效性判定：" + leadName(lead), "OPEN_LEAD_FOLLOW_UP", dueAt,
                JsonUtils.toJsonString(Map.of("roundNo", roundNo, "ruleId", rule.getId(),
                        "ruleVersion", rule.getVersion() == null ? 0 : rule.getVersion(),
                        "timeoutMinutes", timeoutMinutes)), qualificationTaskKey(lead.getId(), roundNo)));
        BusinessEventDO event = new BusinessEventDO();
        event.setEventType(EVENT_LEAD_QUALIFICATION_STARTED);
        event.setAggregateType(BIZ_TYPE_LEAD);
        event.setAggregateId(lead.getId());
        event.setOperatorUserId(assigneeId);
        event.setFromStatus(lead.getStatus());
        event.setToStatus(lead.getStatus());
        event.setRelatedObjectRefs(JsonUtils.toJsonString(Map.of("roundNo", roundNo, "dueAt", dueAt.toString())));
        event.setOccurredAt(startedAt);
        event.setIdempotencyKey("lead-qualification-started:" + lead.getId() + ":" + roundNo);
        eventMapper.insert(event);
        return rule;
    }

    public void completeQualificationTask(Long leadId, Integer roundNo, LocalDateTime completedAt) {
        if (roundNo == null) return;
        taskCommandService.completeByKey(qualificationTaskKey(leadId, roundNo), completedAt);
    }

    public void cancelQualificationTask(Long leadId, Integer roundNo, LocalDateTime cancelledAt, String reason) {
        if (roundNo == null) return;
        var task = taskCommandService.getByIdempotencyKey(qualificationTaskKey(leadId, roundNo));
        if (task != null) {
            taskCommandService.cancel(TASK_TYPE_QUALIFICATION, leadId, task.getAssigneeId(), cancelledAt, reason);
        }
    }

    private BusinessTaskCreateCommand command(String taskType, Long leadId, Long assigneeId, String title,
                                               String actionCode, LocalDateTime dueAt, String payload,
                                               String idempotencyKey) {
        return new BusinessTaskCreateCommand(taskType, BIZ_TYPE_LEAD, leadId, assigneeId, title, null,
                actionCode, dueAt, null, payload, idempotencyKey);
    }

    private String leadName(LeadDO lead) {
        return lead.getSubmittedName() == null || lead.getSubmittedName().isBlank()
                ? "客资 #" + lead.getId() : lead.getSubmittedName();
    }

    private String qualificationTaskKey(Long leadId, int roundNo) {
        return "lead-qualification:" + leadId + ":" + roundNo;
    }

    private void addOwnershipEvent(Long leadId, Long operatorUserId, Long assignmentHistoryId,
                                   LocalDateTime occurredAt, String eventType, String fromAssignmentStatus) {
        BusinessEventDO event = new BusinessEventDO();
        event.setEventType(eventType);
        event.setAggregateType(BIZ_TYPE_LEAD);
        event.setAggregateId(leadId);
        event.setOperatorUserId(operatorUserId);
        event.setFromStatus(fromAssignmentStatus);
        event.setToStatus(ASSIGNMENT_OWNED);
        event.setRelatedObjectRefs(JsonUtils.toJsonString(Map.of("assignmentHistoryId", assignmentHistoryId)));
        event.setOccurredAt(occurredAt);
        event.setIdempotencyKey("lead-ownership:" + assignmentHistoryId);
        eventMapper.insert(event);
    }
}
