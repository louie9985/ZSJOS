package cn.iocoder.yudao.module.zsjos.service.lead;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.LeadDO;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants.SUBORDINATE_LEAD_STATE_INVALID;
import static cn.iocoder.yudao.module.zsjos.service.lead.SupervisorLeadActionPolicy.Action.*;
import static org.junit.jupiter.api.Assertions.*;

class SupervisorLeadActionPolicyTest {

    @Test
    void allowsOnlyTheDocumentedSupervisorStateMatrix() {
        LeadDO submitted = lead("submitted", "owned");
        assertTrue(SupervisorLeadActionPolicy.isAllowed(TRANSFER, submitted));
        assertFalse(SupervisorLeadActionPolicy.isAllowed(RESTORE, submitted));
        assertTrue(SupervisorLeadActionPolicy.isAllowed(RECYCLE, submitted));
        assertTrue(SupervisorLeadActionPolicy.isAllowed(RELEASE_CLAIM_POOL, submitted));
        assertFalse(SupervisorLeadActionPolicy.isAllowed(RELEASE_PUBLIC_SEA, submitted));

        LeadDO suspended = lead("suspended", "owned");
        assertTrue(SupervisorLeadActionPolicy.isAllowed(TRANSFER, suspended));
        assertTrue(SupervisorLeadActionPolicy.isAllowed(RESTORE, suspended));
        assertTrue(SupervisorLeadActionPolicy.isAllowed(RECYCLE, suspended));
        assertTrue(SupervisorLeadActionPolicy.isAllowed(RELEASE_CLAIM_POOL, suspended));

        LeadDO recyclePending = lead("submitted", "recycle_pending");
        assertTrue(SupervisorLeadActionPolicy.isAllowed(TRANSFER, recyclePending));
        assertTrue(SupervisorLeadActionPolicy.isAllowed(RELEASE_CLAIM_POOL, recyclePending));
        assertFalse(SupervisorLeadActionPolicy.isAllowed(RESTORE, recyclePending));
        assertFalse(SupervisorLeadActionPolicy.isAllowed(RECYCLE, recyclePending));

        for (String status : new String[]{"valid", "converted"}) {
            LeadDO valid = lead(status, "owned");
            assertTrue(SupervisorLeadActionPolicy.isAllowed(TRANSFER, valid));
            assertTrue(SupervisorLeadActionPolicy.isAllowed(RELEASE_PUBLIC_SEA, valid));
            assertFalse(SupervisorLeadActionPolicy.isAllowed(RELEASE_CLAIM_POOL, valid));
        }
    }

    @Test
    void rejectsTerminalPoolAndUnknownStates() {
        for (String status : new String[]{"invalid", "won", "closed", "legacy_state"}) {
            LeadDO lead = lead(status, "owned");
            for (SupervisorLeadActionPolicy.Action action : SupervisorLeadActionPolicy.Action.values()) {
                assertFalse(SupervisorLeadActionPolicy.isAllowed(action, lead));
            }
        }
        for (String assignment : new String[]{"unassigned", "pending_acceptance", "public_pool", "closed", "legacy_assignment"}) {
            LeadDO lead = lead("submitted", assignment);
            for (SupervisorLeadActionPolicy.Action action : SupervisorLeadActionPolicy.Action.values()) {
                assertFalse(SupervisorLeadActionPolicy.isAllowed(action, lead));
            }
        }
    }

    @Test
    void stateErrorContainsActionCurrentStateAndRequirement() {
        LeadDO lead = lead("legacy_state", "legacy_assignment");
        lead.setClosedAt(LocalDateTime.of(2026, 8, 26, 12, 0));

        ServiceException error = assertThrows(ServiceException.class,
                () -> SupervisorLeadActionPolicy.requireAllowed(RELEASE_PUBLIC_SEA, lead));

        assertEquals(SUBORDINATE_LEAD_STATE_INVALID.getCode(), error.getCode());
        assertTrue(error.getMessage().contains("释放至公海池"));
        assertTrue(error.getMessage().contains("未知客资状态（legacy_state）"));
        assertTrue(error.getMessage().contains("未知分配状态（legacy_assignment）"));
        assertTrue(error.getMessage().contains("已设置关闭时间"));
        assertTrue(error.getMessage().contains("有效或已转化 / 已归属且未关闭"));
    }

    private static LeadDO lead(String status, String assignmentStatus) {
        LeadDO lead = new LeadDO();
        lead.setStatus(status);
        lead.setAssignmentStatus(assignmentStatus);
        return lead;
    }
}
