package cn.iocoder.yudao.module.zsjos.service.lead;

import cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.LeadDO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LeadRestoreOwnerPolicyTest {
    @InjectMocks private LeadRestoreOwnerPolicy policy;
    @Mock private LeadAssignmentService assignmentService;
    @Mock private LeadSubmissionIdentityService identityService;

    @Test void educationSelfSourcedUsesEducationEligibilityOnly() {
        LeadDO lead = lead("education", "education_self_sourced");
        when(identityService.isEligibleEducationOwner(20L)).thenReturn(true);
        assertTrue(policy.isEligible(lead));
        when(identityService.isEligibleEducationOwner(20L)).thenReturn(false);
        assertFalse(policy.isEligible(lead));
        verifyNoInteractions(assignmentService);
    }

    @Test void otherEducationSourcesDoNotGainRestoration() {
        assertFalse(policy.isEligible(lead("education", "internal_new_media")));
        verifyNoInteractions(identityService, assignmentService);
    }

    @Test void salesAndLegacyOwnersRetainSalesEligibility() {
        when(assignmentService.isEligibleSalesUser(20L)).thenReturn(true);
        assertTrue(policy.isEligible(lead("sales", "sales_self_sourced")));
        assertTrue(policy.isEligible(lead(null, "internal_new_media")));
        when(assignmentService.isEligibleSalesUser(20L)).thenReturn(false);
        assertFalse(policy.isEligible(lead("sales", "sales_self_sourced")));
        verifyNoInteractions(identityService);
    }

    @Test void absentOwnerCannotBeRestored() {
        LeadDO lead = lead("education", "education_self_sourced");
        lead.setOwnerUserId(null);
        assertFalse(policy.isEligible(lead));
        verifyNoInteractions(identityService, assignmentService);
    }

    private LeadDO lead(String identity, String source) {
        LeadDO lead = new LeadDO();
        lead.setOwnerUserId(20L); lead.setOwnerIdentity(identity); lead.setSourceType(source);
        return lead;
    }
}
