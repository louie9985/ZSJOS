package cn.iocoder.yudao.module.zsjos.service.lead;

import cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.LeadDO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
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

    @ParameterizedTest
    @CsvSource({"education_self_sourced,self", "partner,specified"})
    void supportedEducationOwnershipUsesEmployeeEligibilityOnly(String source, String dispatchMode) {
        LeadDO lead = lead("education", source);
        lead.setDispatchMode(dispatchMode);
        when(identityService.isEligibleEducationOwner(20L)).thenReturn(true);
        assertTrue(policy.isEligible(lead));
        when(identityService.isEligibleEducationOwner(20L)).thenReturn(false);
        assertFalse(policy.isEligible(lead));
        verifyNoInteractions(assignmentService);
    }

    @ParameterizedTest
    @CsvSource({"internal_new_media,specified", "sales_self_sourced,self", "partner,auto",
            "partner,self", "partner,", ",specified"})
    void otherEducationSourcesDoNotGainRestoration(String source, String dispatchMode) {
        LeadDO lead = lead("education", source);
        lead.setDispatchMode(dispatchMode);
        assertFalse(policy.isEligible(lead));
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
    @Test void assistedEducationRetainsRestorationAfterSourceCorrection() {
        LeadDO lead=lead("education","internal_new_media");
        lead.setSubmissionSourceType("education_self_sourced");lead.setDispatchMode("self");
        when(identityService.isEligibleEducationOwner(20L)).thenReturn(true);
        assertTrue(policy.isEligible(lead));verifyNoInteractions(assignmentService);
        lead.setSubmissionSourceType("sales_self_sourced");assertFalse(policy.isEligible(lead));
    }

}
