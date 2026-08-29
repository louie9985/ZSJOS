package cn.iocoder.yudao.module.zsjos.service.lead;

import cn.iocoder.yudao.module.zsjos.dal.dataobject.event.BusinessEventDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.LeadFollowUpRuleDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.LeadDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.event.BusinessEventMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.lead.LeadMapper;
import cn.iocoder.yudao.module.zsjos.service.task.BusinessTaskCommandService;
import cn.iocoder.yudao.module.zsjos.service.task.BusinessTaskCreateCommand;
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
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LeadLifecycleTaskServiceTest {
    @InjectMocks private LeadLifecycleTaskService service;
    @Mock private BusinessTaskCommandService taskCommandService;
    @Mock private BusinessEventMapper eventMapper;
    @Mock private LeadMapper leadMapper;
    @Mock private LeadFollowUpRuleService followUpRuleService;

    @Test
    void createsFirstFollowUpTaskFromCurrentRuleSnapshot() {
        LeadFollowUpRuleDO rule = new LeadFollowUpRuleDO();
        rule.setId(7L); rule.setVersion(3); rule.setFirstFollowUpTimeoutMinutes(90);
        when(followUpRuleService.requireEnabledRule()).thenReturn(rule);
        LeadDO lead = new LeadDO(); lead.setId(1L); lead.setLeadNo("KZ202608160000000001");
        when(leadMapper.selectById(1L)).thenReturn(lead);
        LocalDateTime acceptedAt = LocalDateTime.of(2026, 8, 9, 10, 0);

        service.createFirstFollowUpTask(1L, 10L, 88L, acceptedAt,
                "lead_assignment_accepted", "pending_acceptance");

        ArgumentCaptor<BusinessTaskCreateCommand> taskCaptor = ArgumentCaptor.forClass(BusinessTaskCreateCommand.class);
        verify(taskCommandService).create(taskCaptor.capture());
        BusinessTaskCreateCommand task = taskCaptor.getValue();
        assertEquals("lead_first_follow_up", task.taskType());
        assertEquals(acceptedAt.plusMinutes(90), task.dueAt());
        assertEquals("lead-first-follow-up:88", task.idempotencyKey());
        assertEquals("首次跟进：KZ202608160000000001", task.title());
        assertTrue(task.payload().contains("\"ruleVersion\":3"));

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
        lead.setId(1L); lead.setLeadNo("KZ202608160000000001");
        lead.setStatus("submitted"); lead.setQualificationRoundNo(2);
        LocalDateTime startedAt = LocalDateTime.of(2026, 8, 9, 10, 0);

        service.createQualificationTask(lead, 10L, startedAt);

        ArgumentCaptor<BusinessTaskCreateCommand> taskCaptor = ArgumentCaptor.forClass(BusinessTaskCreateCommand.class);
        verify(taskCommandService).create(taskCaptor.capture());
        BusinessTaskCreateCommand task = taskCaptor.getValue();
        assertEquals("lead_qualification", task.taskType());
        assertEquals(startedAt.plusMinutes(4320), task.dueAt());
        assertEquals("lead-qualification:1:3", task.idempotencyKey());
        assertEquals(3, lead.getQualificationRoundNo());
        assertEquals(startedAt.plusMinutes(4320), lead.getQualificationDeadlineAt());
        assertTrue(lead.getQualificationRuleSnapshot().contains("\"ruleVersion\":4"));
        assertTrue(lead.getQualificationRuleSnapshot().contains("\"timeoutMinutes\":4320"));
    }

    @Test
    void createsDistinctReminderKeysForLeadAndOpportunityRecordsWithSameId() {
        LocalDateTime changedAt = LocalDateTime.of(2026, 8, 10, 10, 0);
        LocalDateTime dueAt = changedAt.plusDays(1);
        LeadDO lead = new LeadDO(); lead.setId(8L); lead.setLeadNo("KZ202608160000000008");
        when(leadMapper.selectById(8L)).thenReturn(lead);

        service.replaceFollowUpReminder(8L, 10L, "lead", 1L, dueAt, changedAt);
        service.replaceFollowUpReminder(8L, 10L, "opportunity", 1L, dueAt, changedAt);

        ArgumentCaptor<BusinessTaskCreateCommand> taskCaptor = ArgumentCaptor.forClass(BusinessTaskCreateCommand.class);
        verify(taskCommandService, times(2)).create(taskCaptor.capture());
        List<BusinessTaskCreateCommand> tasks = taskCaptor.getAllValues();
        assertEquals("lead-follow-up-reminder:lead:1", tasks.get(0).idempotencyKey());
        assertEquals("lead-follow-up-reminder:opportunity:1", tasks.get(1).idempotencyKey());
        assertNotEquals(tasks.get(0).idempotencyKey(), tasks.get(1).idempotencyKey());
        assertTrue(tasks.get(0).payload().contains("\"followUpRecordScope\":\"lead\""));
        assertTrue(tasks.get(1).payload().contains("\"followUpRecordScope\":\"opportunity\""));
    }

    @Test
    void createsSubmitterAssistTaskForInternalSubmitterOnly() {
        LeadDO lead = new LeadDO();
        lead.setId(1L); lead.setLeadNo("KZ202608160000000001"); lead.setSourceUserId(30L);
        LocalDateTime occurredAt = LocalDateTime.of(2026, 8, 29, 10, 0);

        service.createSubmitterAssistTask(lead, 40L, occurredAt, "未联系上", "电话未接");

        ArgumentCaptor<BusinessTaskCreateCommand> taskCaptor = ArgumentCaptor.forClass(BusinessTaskCreateCommand.class);
        verify(taskCommandService).create(taskCaptor.capture());
        BusinessTaskCreateCommand task = taskCaptor.getValue();
        assertEquals("lead_submitter_assist", task.taskType());
        assertEquals("lead", task.bizType());
        assertEquals(1L, task.bizId());
        assertEquals(30L, task.assigneeId());
        assertEquals("OPEN_LEAD_SUBMITTER_SUPPLEMENT", task.actionCode());
        assertEquals("lead-submitter-assist:40", task.idempotencyKey());
        assertTrue(task.payload().contains("\"followUpRecordId\":40"));
    }

    @Test
    void skipsSubmitterAssistTaskForPartnerSubmitterWithoutAdminUser() {
        LeadDO lead = new LeadDO();
        lead.setId(1L); lead.setLeadNo("KZ202608160000000001");

        service.createSubmitterAssistTask(lead, 40L, LocalDateTime.now(), "未联系上", "电话未接");

        verifyNoInteractions(taskCommandService);
    }
}
