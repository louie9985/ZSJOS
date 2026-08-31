package cn.iocoder.yudao.module.zsjos.service.lead;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static cn.iocoder.yudao.module.zsjos.enums.LeadConstants.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LeadSimpleStatusQueryTest {

    @Test
    void resolvesPendingHandlingStatuses() {
        assertFilter("first_follow_pending", Set.of(STATUS_SUBMITTED), Set.of(ASSIGNMENT_OWNED),
                Set.of(LeadHandlingStage.FIRST_FOLLOW_PENDING));
        assertFilter("qualification_pending", Set.of(STATUS_SUBMITTED), Set.of(ASSIGNMENT_OWNED),
                Set.of(LeadHandlingStage.QUALIFICATION_PENDING));
    }

    @Test
    void resolvesSalesProgressAndTerminalStatuses() {
        LeadSimpleStatusQuery following = LeadSimpleStatusQuery.resolve("following");
        assertEquals(Set.of(STATUS_VALID), following.leadStatuses());
        assertEquals(Set.of(OPPORTUNITY_STATUS_DEAL_PENDING_APPROVAL, OPPORTUNITY_STATUS_WON),
                following.excludedOpportunityStatuses());

        LeadSimpleStatusQuery pendingDeal = LeadSimpleStatusQuery.resolve("deal_pending_approval");
        assertEquals(Set.of(STATUS_VALID), pendingDeal.leadStatuses());
        assertEquals(Set.of(OPPORTUNITY_STATUS_DEAL_PENDING_APPROVAL), pendingDeal.requiredOpportunityStatuses());

        assertFilter("won", Set.of(STATUS_WON), Set.of(), Set.of());
        assertFilter("invalid", Set.of(STATUS_INVALID), Set.of(), Set.of());
        assertFilter("closed", Set.of(STATUS_CLOSED), Set.of(), Set.of());
        assertFilter("suspended", Set.of(STATUS_SUSPENDED), Set.of(), Set.of());
        assertFilter("all", Set.of(), Set.of(), Set.of());
        assertThrows(IllegalArgumentException.class, () -> LeadSimpleStatusQuery.resolve("unassigned"));
    }

    private static void assertFilter(String key, Set<String> statuses, Set<String> assignments,
                                     Set<String> handlingStages) {
        LeadSimpleStatusQuery query = LeadSimpleStatusQuery.resolve(key);
        assertEquals(statuses, query.leadStatuses());
        assertEquals(assignments, query.assignmentStatuses());
        assertEquals(handlingStages, query.handlingStages());
    }
}
