package cn.iocoder.yudao.module.zsjos.service.lead;

import cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.LeadAppealDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.LeadDO;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class LeadAppealCyclesTest {
    private LeadAppealDO appeal(long cycle, int round, String status) {
        var a = new LeadAppealDO(); a.setInvalidEventId(cycle); a.setRoundNo(round);
        a.setStatus(status); a.setReviewStage(round == 1 ? "sales_manager" : round == 2 ? "quality" : "chairman");
        return a;
    }
    @Test void newDecisionRestartsAtSupervisorWithoutConsumingOldRounds() {
        var old = List.of(appeal(11, 1, "upheld"), appeal(11, 2, "upheld"), appeal(11, 3, "overturned"));
        assertEquals(1, LeadAppealCycles.nextRound(old, 22));
        assertNull(LeadAppealCycles.nextRound(old, 11));
        var lead = new LeadDO(); lead.setStatus("invalid"); lead.setAssignmentStatus("owned"); lead.setOwnerUserId(10L);
        assertNull(SupervisorLeadOverturnPolicy.rejection(lead, old, null, 22));
        assertNotNull(SupervisorLeadOverturnPolicy.rejection(lead, old, null, 11));
    }
    @Test void sameDecisionEscalatesOnlyAfterUpheldAndStopsAfterThirdRound() {
        assertEquals(2, LeadAppealCycles.nextRound(List.of(appeal(11, 1, "upheld")), 11));
        assertEquals(3, LeadAppealCycles.nextRound(List.of(appeal(11, 1, "upheld"), appeal(11, 2, "upheld")), 11));
        assertNull(LeadAppealCycles.nextRound(List.of(appeal(11, 3, "upheld")), 11));
        assertNull(LeadAppealCycles.nextRound(List.of(appeal(11, 1, "withdrawn")), 11));
    }
    @Test void activeOrUncorrelatedHistoryNeverGetsBypassed() {
        for (String status : List.of("sales_manager_reviewing", "quality_reviewing", "chairman_reviewing", "pending")) {
            assertNull(LeadAppealCycles.nextRound(List.of(appeal(11, 1, status)), 22));
        }
        assertNull(LeadAppealCycles.nextRound(List.of(appeal(0, 1, "overturned")), 22));
        assertEquals(1, LeadAppealCycles.nextRound(List.of(), 0));
    }
}
