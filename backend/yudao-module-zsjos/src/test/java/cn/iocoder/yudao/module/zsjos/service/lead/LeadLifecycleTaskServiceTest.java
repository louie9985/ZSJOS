package cn.iocoder.yudao.module.zsjos.service.lead;

import cn.iocoder.yudao.module.zsjos.dal.dataobject.event.BusinessEventDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.LeadFollowUpRuleDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.LeadDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.task.BusinessTaskDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.event.BusinessEventMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.task.BusinessTaskMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LeadLifecycleTaskServiceTest {
    @InjectMocks private LeadLifecycleTaskService service;
    @Mock private BusinessTaskMapper taskMapper;
    @Mock private BusinessEventMapper eventMapper;
    @Mock private LeadFollowUpRuleService followUpRuleService;

    @Test
    void createsFirstFollowUpTaskFromCurrentRuleSnapshot() {
        LeadFollowUpRuleDO rule = new LeadFollowUpRuleDO();
        rule.setId(7L); rule.setVersion(3); rule.setFirstFollowUpTimeoutMinutes(90);
        when(followUpRuleService.requireEnabledRule()).thenReturn(rule);
        LocalDateTime acceptedAt = LocalDateTime.of(2026, 8, 9, 10, 0);

        service.createFirstFollowUpTask(1L, 10L, 88L, acceptedAt,
                "lead_assignment_accepted", "pending_acceptance");

        ArgumentCaptor<BusinessTaskDO> taskCaptor = ArgumentCaptor.forClass(BusinessTaskDO.class);
        verify(taskMapper).insert(taskCaptor.capture());
        BusinessTaskDO task = taskCaptor.getValue();
        assertEquals("lead_first_follow_up", task.getTaskType());
        assertEquals(acceptedAt.plusMinutes(90), task.getDueAt());
        assertEquals("lead-first-follow-up:88", task.getIdempotencyKey());
        assertTrue(task.getPayload().contains("\"ruleVersion\":3"));

        ArgumentCaptor<BusinessEventDO> eventCaptor = ArgumentCaptor.forClass(BusinessEventDO.class);
        verify(eventMapper).insert(eventCaptor.capture());
        assertEquals("lead_assignment_accepted", eventCaptor.getValue().getEventType());
        assertEquals("lead-ownership:88", eventCaptor.getValue().getIdempotencyKey());
    }

    @Test
    void createsQualificationTaskWithImmutableRuleSnapshot() {
        LeadFollowUpRuleDO rule = new LeadFollowUpRuleDO();
        rule.setId(7L); rule.setVersion(4); rule.setQualificationTimeoutMinutes(4320);
        when(followUpRuleService.requireEnabledRule()).thenReturn(rule);
        LeadDO lead = new LeadDO();
        lead.setId(1L); lead.setStatus("submitted"); lead.setQualificationRoundNo(2);
        LocalDateTime startedAt = LocalDateTime.of(2026, 8, 9, 10, 0);

        service.createQualificationTask(lead, 10L, startedAt);

        ArgumentCaptor<BusinessTaskDO> taskCaptor = ArgumentCaptor.forClass(BusinessTaskDO.class);
        verify(taskMapper).insert(taskCaptor.capture());
        BusinessTaskDO task = taskCaptor.getValue();
        assertEquals("lead_qualification", task.getTaskType());
        assertEquals(startedAt.plusMinutes(4320), task.getDueAt());
        assertEquals("lead-qualification:1:3", task.getIdempotencyKey());
        assertEquals(3, lead.getQualificationRoundNo());
        assertEquals(startedAt.plusMinutes(4320), lead.getQualificationDeadlineAt());
        assertTrue(lead.getQualificationRuleSnapshot().contains("\"ruleVersion\":4"));
        assertTrue(lead.getQualificationRuleSnapshot().contains("\"timeoutMinutes\":4320"));
    }

    @Test
    void createsDistinctReminderKeysForLeadAndOpportunityRecordsWithSameId() {
        LocalDateTime changedAt = LocalDateTime.of(2026, 8, 10, 10, 0);
        LocalDateTime dueAt = changedAt.plusDays(1);

        service.replaceFollowUpReminder(8L, 10L, "lead", 1L, dueAt, changedAt);
        service.replaceFollowUpReminder(8L, 10L, "opportunity", 1L, dueAt, changedAt);

        ArgumentCaptor<BusinessTaskDO> taskCaptor = ArgumentCaptor.forClass(BusinessTaskDO.class);
        verify(taskMapper, times(2)).insert(taskCaptor.capture());
        List<BusinessTaskDO> tasks = taskCaptor.getAllValues();
        assertEquals("lead-follow-up-reminder:lead:1", tasks.get(0).getIdempotencyKey());
        assertEquals("lead-follow-up-reminder:opportunity:1", tasks.get(1).getIdempotencyKey());
        assertNotEquals(tasks.get(0).getIdempotencyKey(), tasks.get(1).getIdempotencyKey());
        assertTrue(tasks.get(0).getPayload().contains("\"followUpRecordScope\":\"lead\""));
        assertTrue(tasks.get(1).getPayload().contains("\"followUpRecordScope\":\"opportunity\""));
    }
}
