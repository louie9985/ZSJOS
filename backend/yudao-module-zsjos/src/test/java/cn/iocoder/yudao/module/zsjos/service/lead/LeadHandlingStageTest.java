package cn.iocoder.yudao.module.zsjos.service.lead;

import cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.LeadDO;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static cn.iocoder.yudao.module.zsjos.enums.LeadConstants.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

class LeadHandlingStageTest {

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
