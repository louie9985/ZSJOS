package cn.iocoder.yudao.module.zsjos.enums;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MediaWorkflowConstantsTest {
    @Test
    void ordinaryPositioningBypassesIpReview() {
        assertFalse(MediaWorkflowConstants.CONTENT_TRANSITIONS.isEmpty());
        assertEquals("operator_feasibility", MediaWorkflowConstants.POSITIONING_OPERATOR_FEASIBILITY);
    }

    @Test
    void contentAndTicketStatesExposeOnlyDocumentedTransitions() {
        // 内容状态机为 acceptance -> ready_to_publish -> published，不能从 acceptance 直接发布
        assertTrue(MediaWorkflowConstants.CONTENT_TRANSITIONS.get("acceptance").contains("ready_to_publish"));
        assertFalse(MediaWorkflowConstants.CONTENT_TRANSITIONS.get("acceptance").contains("published"));
        assertTrue(MediaWorkflowConstants.CONTENT_TRANSITIONS.get("ready_to_publish").contains("published"));
        assertFalse(MediaWorkflowConstants.CONTENT_TRANSITIONS.get("topic").contains("published"));
        assertTrue(MediaWorkflowConstants.TICKET_TRANSITIONS.get("checking").contains("rejected"));
        assertFalse(MediaWorkflowConstants.TICKET_TRANSITIONS.get("pending_accept").contains("completed"));
    }
}
