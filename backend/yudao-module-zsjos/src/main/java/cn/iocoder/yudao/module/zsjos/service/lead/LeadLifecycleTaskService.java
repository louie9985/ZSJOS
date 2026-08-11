package cn.iocoder.yudao.module.zsjos.service.lead;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.event.BusinessEventDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.LeadFollowUpRuleDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.LeadDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.task.BusinessTaskDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.event.BusinessEventMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.task.BusinessTaskMapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Map;

import static cn.iocoder.yudao.module.zsjos.enums.LeadConstants.*;

@Service
public class LeadLifecycleTaskService {
    @Resource private BusinessTaskMapper taskMapper;
    @Resource private BusinessEventMapper eventMapper;
    @Resource private LeadFollowUpRuleService followUpRuleService;

    public void createAssignmentTask(Long leadId, Long assigneeId, Long assignmentHistoryId,
                                     LocalDateTime dueAt, String dispatchMode) {
        BusinessTaskDO task = new BusinessTaskDO();
        task.setTaskType(TASK_TYPE_ASSIGNMENT_ACCEPT);
        task.setBizType(BIZ_TYPE_LEAD);
        task.setBizId(leadId);
        task.setStatus(TASK_STATUS_PENDING);
        task.setAssigneeType(ASSIGNEE_TYPE_USER);
        task.setAssigneeId(assigneeId);
        task.setDueAt(dueAt);
        task.setPayload(JsonUtils.toJsonString(Map.of(
                "assignmentHistoryId", assignmentHistoryId,
                "dispatchMode", dispatchMode)));
        task.setIdempotencyKey("lead-assignment-accept:" + assignmentHistoryId);
        task.setVersion(0);
        taskMapper.insert(task);
    }

    public void completeAssignmentTask(Long leadId, Long assigneeId, LocalDateTime completedAt) {
        taskMapper.completePending(TASK_TYPE_ASSIGNMENT_ACCEPT, leadId, assigneeId, completedAt);
    }

    public void cancelAssignmentTask(Long leadId, Long assigneeId, LocalDateTime cancelledAt, String reason) {
        taskMapper.cancelPending(TASK_TYPE_ASSIGNMENT_ACCEPT, leadId, assigneeId, cancelledAt, reason);
    }

    public LocalDateTime createFirstFollowUpTask(Long leadId, Long assigneeId, Long assignmentHistoryId,
                                        LocalDateTime ownershipStartedAt, String eventType,
                                        String fromAssignmentStatus) {
        LeadFollowUpRuleDO rule = followUpRuleService.requireEnabledRule();
        int timeoutMinutes = rule.getFirstFollowUpTimeoutMinutes();
        BusinessTaskDO task = new BusinessTaskDO();
        task.setTaskType(TASK_TYPE_FIRST_FOLLOW_UP);
        task.setBizType(BIZ_TYPE_LEAD);
        task.setBizId(leadId);
        task.setStatus(TASK_STATUS_PENDING);
        task.setAssigneeType(ASSIGNEE_TYPE_USER);
        task.setAssigneeId(assigneeId);
        LocalDateTime dueAt = ownershipStartedAt.plusMinutes(timeoutMinutes);
        task.setDueAt(dueAt);
        task.setPayload(JsonUtils.toJsonString(Map.of(
                "assignmentHistoryId", assignmentHistoryId,
                "ruleId", rule.getId(),
                "ruleVersion", rule.getVersion() == null ? 0 : rule.getVersion(),
                "timeoutMinutes", timeoutMinutes,
                "ownershipStartedAt", ownershipStartedAt.toString())));
        task.setIdempotencyKey("lead-first-follow-up:" + assignmentHistoryId);
        task.setVersion(0);
        taskMapper.insert(task);
        addOwnershipEvent(leadId, assigneeId, assignmentHistoryId, ownershipStartedAt,
                eventType, fromAssignmentStatus);
        return dueAt;
    }

    public void cancelFirstFollowUpTasks(Long leadId, LocalDateTime cancelledAt, String reason) {
        taskMapper.cancelPending(TASK_TYPE_FIRST_FOLLOW_UP, leadId, null, cancelledAt, reason);
    }

    public boolean completeFirstFollowUpTask(Long assignmentHistoryId, LocalDateTime completedAt) {
        return taskMapper.completePendingByKey("lead-first-follow-up:" + assignmentHistoryId, completedAt) > 0;
    }

    public void replaceFollowUpReminder(Long leadId, Long assigneeId, String recordScope, Long recordId,
                                        LocalDateTime dueAt, LocalDateTime changedAt) {
        taskMapper.completePending(TASK_TYPE_FOLLOW_UP_REMINDER, leadId, assigneeId, changedAt);
        if (dueAt == null) return;
        BusinessTaskDO task = new BusinessTaskDO();
        task.setTaskType(TASK_TYPE_FOLLOW_UP_REMINDER);
        task.setBizType(BIZ_TYPE_LEAD);
        task.setBizId(leadId);
        task.setStatus(TASK_STATUS_PENDING);
        task.setAssigneeType(ASSIGNEE_TYPE_USER);
        task.setAssigneeId(assigneeId);
        task.setDueAt(dueAt);
        task.setPayload(JsonUtils.toJsonString(Map.of(
                "followUpRecordScope", recordScope,
                "followUpRecordId", recordId)));
        task.setIdempotencyKey("lead-follow-up-reminder:" + recordScope + ":" + recordId);
        task.setVersion(0);
        taskMapper.insert(task);
    }

    public void cancelFollowUpReminders(Long leadId, LocalDateTime cancelledAt, String reason) {
        taskMapper.cancelPending(TASK_TYPE_FOLLOW_UP_REMINDER, leadId, null, cancelledAt, reason);
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

        BusinessTaskDO task = new BusinessTaskDO();
        task.setTaskType(TASK_TYPE_QUALIFICATION);
        task.setBizType(BIZ_TYPE_LEAD);
        task.setBizId(lead.getId());
        task.setStatus(TASK_STATUS_PENDING);
        task.setAssigneeType(ASSIGNEE_TYPE_USER);
        task.setAssigneeId(assigneeId);
        task.setDueAt(dueAt);
        task.setPayload(JsonUtils.toJsonString(Map.of(
                "roundNo", roundNo,
                "ruleId", rule.getId(),
                "ruleVersion", rule.getVersion() == null ? 0 : rule.getVersion(),
                "timeoutMinutes", timeoutMinutes)));
        task.setIdempotencyKey(qualificationTaskKey(lead.getId(), roundNo));
        task.setVersion(0);
        taskMapper.insert(task);
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
        taskMapper.completePendingByKey(qualificationTaskKey(leadId, roundNo), completedAt);
    }

    public void cancelQualificationTask(Long leadId, Integer roundNo, LocalDateTime cancelledAt, String reason) {
        if (roundNo == null) return;
        BusinessTaskDO task = taskMapper.selectByIdempotencyKey(qualificationTaskKey(leadId, roundNo));
        if (task != null) {
            taskMapper.cancelPending(TASK_TYPE_QUALIFICATION, leadId, task.getAssigneeId(), cancelledAt, reason);
        }
    }

    public Long getQualificationTaskId(Long leadId, Integer roundNo) {
        if (roundNo == null) return null;
        BusinessTaskDO task = taskMapper.selectByIdempotencyKey(qualificationTaskKey(leadId, roundNo));
        return task == null ? null : task.getId();
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
