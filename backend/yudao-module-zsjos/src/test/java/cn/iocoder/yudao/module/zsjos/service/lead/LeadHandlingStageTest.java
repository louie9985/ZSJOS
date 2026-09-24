package cn.iocoder.yudao.module.zsjos.service.lead;

import cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.LeadDO;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static cn.iocoder.yudao.module.zsjos.enums.LeadConstants.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

class LeadHandlingStageTest {

    @Test
    void followUpProjectionUsesFirstFactNotQualificationTimer() {
        LeadDO lead = new LeadDO().setStatus(STATUS_SUBMITTED).setAssignmentStatus(ASSIGNMENT_OWNED);
        lead.setCurrentAssignmentFirstFollowUpAt(LocalDateTime.now());
        assertEquals(FOLLOW_UP_FOLLOWING, LeadStateProjection.followUp(lead, null));
        lead.setCurrentAssignmentFirstFollowUpAt(null);
        lead.setCurrentAssignmentFirstFollowUpDeadlineAt(LocalDateTime.now());
        lead.setQualificationDeadlineAt(LocalDateTime.now().plusDays(3));
        assertEquals(FOLLOW_UP_FIRST_PENDING, LeadStateProjection.followUp(lead, null));
        lead.setCurrentAssignmentFirstFollowUpDeadlineAt(null);
        assertEquals(FOLLOW_UP_FOLLOWING, LeadStateProjection.followUp(lead, null));
    }

    @Test
    void legacyWonLeadWithoutOpportunityStillProjectsAsWon() {
        LeadDO lead = new LeadDO().setStatus(STATUS_WON);

        assertEquals(FOLLOW_UP_WON, LeadStateProjection.followUp(lead, null));
    }

    @Test
    void qualificationDeadlineDoesNotReplaceFirstFollowStage() {
        LeadDO lead = new LeadDO();
        lead.setStatus(STATUS_SUBMITTED);
        lead.setAssignmentStatus(ASSIGNMENT_OWNED);
        lead.setQualificationDeadlineAt(LocalDateTime.now().plusHours(1));
        lead.setCurrentAssignmentFirstFollowUpAt(null);

        assertEquals(LeadHandlingStage.FIRST_FOLLOW_PENDING, LeadHandlingStage.resolve(lead));
    }

    @Test
    void completedFirstFollowMovesToQualificationStage() {
        LeadDO lead = new LeadDO();
        lead.setStatus(STATUS_SUBMITTED);
        lead.setAssignmentStatus(ASSIGNMENT_OWNED);
        lead.setQualificationDeadlineAt(LocalDateTime.now().plusHours(1));
        lead.setCurrentAssignmentFirstFollowUpAt(LocalDateTime.now());

        assertEquals(LeadHandlingStage.QUALIFICATION_PENDING, LeadHandlingStage.resolve(lead));
    }
}
