package cn.iocoder.yudao.module.zsjos.service.lead;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.LeadPublicSeaPoolCycleDO;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class LeadPublicSeaWritePolicyTest {
    @Test void stateAndActorMatrix() {
        var cycle = new LeadPublicSeaPoolCycleDO();
        cycle.setOriginalOwnerUserId(20L);
        cycle.setStatus("waiting_assignment");
        assertTrue(LeadPublicSeaWritePolicy.canFollow(cycle, 20L));
        assertTrue(LeadPublicSeaWritePolicy.canEdit(cycle, 20L));
        assertFalse(LeadPublicSeaWritePolicy.canFollow(cycle, 30L));
        assertFalse(LeadPublicSeaWritePolicy.canEdit(cycle, 30L));
        cycle.setCollaboratorUserId(30L);
        for (String status : java.util.List.of("assigned", "deal_pending", "exited", "converted")) {
            cycle.setStatus(status);
            boolean active = status.equals("assigned") || status.equals("deal_pending");
            assertEquals(status.equals("assigned"), LeadPublicSeaWritePolicy.canFollow(cycle, 20L));
            assertEquals(active, LeadPublicSeaWritePolicy.canFollow(cycle, 30L));
            assertEquals(active, LeadPublicSeaWritePolicy.canEdit(cycle, 20L));
            assertEquals(active, LeadPublicSeaWritePolicy.canEdit(cycle, 30L));
            assertFalse(LeadPublicSeaWritePolicy.canFollow(cycle, 40L));
            assertFalse(LeadPublicSeaWritePolicy.canEdit(cycle, 40L));
            assertFalse(LeadPublicSeaWritePolicy.canEdit(cycle, null));
        }
        cycle.setStatus("assigned"); cycle.setCollaboratorUserId(40L);
        assertFalse(LeadPublicSeaWritePolicy.canFollow(cycle, 30L));
        assertFalse(LeadPublicSeaWritePolicy.canEdit(cycle, 30L));
    }
}
