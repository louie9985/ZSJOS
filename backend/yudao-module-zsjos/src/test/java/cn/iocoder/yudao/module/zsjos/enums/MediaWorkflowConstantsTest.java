package cn.iocoder.yudao.module.zsjos.enums;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MediaWorkflowConstantsTest {
    @Test
    void ordinaryPositioningBypassesIpReview() {
        assertFalse(MediaWorkflowConstants.CONTENT_TRANSITIONS.isEmpty());
        assertTrue(MediaWorkflowConstants.ACCOUNT_STAGES.contains("s0"));
        assertEquals("operator_feasibility", MediaWorkflowConstants.POSITIONING_OPERATOR_FEASIBILITY);
    }

    @Test
    void contentAndTicketStatesExposeOnlyDocumentedTransitions() {
        assertTrue(MediaWorkflowConstants.CONTENT_TRANSITIONS.get("acceptance").contains("published"));
        assertFalse(MediaWorkflowConstants.CONTENT_TRANSITIONS.get("topic").contains("published"));
        assertTrue(MediaWorkflowConstants.TICKET_TRANSITIONS.get("checking").contains("rejected"));
        assertFalse(MediaWorkflowConstants.TICKET_TRANSITIONS.get("pending_accept").contains("completed"));
    }
}
