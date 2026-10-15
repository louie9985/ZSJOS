package cn.iocoder.yudao.module.zsjos.service.lead.flow;

import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class LeadFlowSnapshotTest {
    @Test void clearingIsDifferentFromMissingHistory() {
        var old = LeadFlowSnapshot.Value.text("2026-10-01T12:00:00");
        var changes = LeadFlowSnapshot.diff(Map.of("suspendedAt",old),
                Map.of("suspendedAt",LeadFlowSnapshot.Value.text(null)),Map.of("suspendedAt","挂起时间"));
        assertEquals("VALUE",changes.getFirst().before().state());
        assertEquals("EMPTY",changes.getFirst().after().state());
        assertEquals("UNRECORDED",LeadFlowSnapshot.diff(Map.of(),Map.of("suspendedAt",old),
                Map.of("suspendedAt","挂起时间")).getFirst().before().state());
    }
    @Test void renamedIdentityDoesNotInventBusinessChange() {
        var old = new LeadFlowSnapshot.Value("VALUE","42","原姓名","ADMIN",42L);
        var renamed = new LeadFlowSnapshot.Value("VALUE","42","新姓名","ADMIN",42L);
        assertTrue(LeadFlowSnapshot.diff(Map.of("owner",old),Map.of("owner",renamed),Map.of("owner","负责人")).isEmpty());
        var partner = new LeadFlowSnapshot.Value("VALUE","42","原姓名","PARTNER",42L);
        assertEquals(1,LeadFlowSnapshot.diff(Map.of("owner",old),Map.of("owner",partner),Map.of("owner","负责人")).size());
    }
}
