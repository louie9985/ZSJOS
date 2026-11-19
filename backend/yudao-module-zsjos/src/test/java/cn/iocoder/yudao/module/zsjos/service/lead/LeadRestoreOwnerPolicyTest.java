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
    @org.mockito.Spy private LeadTeamTransferPolicy teamTransferPolicy = new LeadTeamTransferPolicy();
    @Mock private LeadSubmissionIdentityService identityService;

    @ParameterizedTest
    @CsvSource({"sales_self_sourced,self", "partner,specified", "internal_new_media,auto", "partner,auto"})
    void salesOwnershipUsesEmployeeEligibilityForEverySource(String source, String dispatchMode) {
        LeadDO lead = lead("sales", source);
        lead.setDispatchMode(dispatchMode);
        when(identityService.isEligibleRetainedOwner(20L)).thenReturn(true);
        assertTrue(policy.isEligible(lead));
        when(identityService.isEligibleRetainedOwner(20L)).thenReturn(false);
        assertFalse(policy.isEligible(lead));
    }

    @Test void retiredEducationOrUnknownIdentityIsRejected() {
        assertThrows(cn.iocoder.yudao.framework.common.exception.ServiceException.class,
                () -> policy.isEligible(lead("education", "partner")));
        assertThrows(cn.iocoder.yudao.framework.common.exception.ServiceException.class,
                () -> policy.isEligible(lead(null, "internal_new_media")));
        verifyNoInteractions(identityService);
    }

    @Test void absentOwnerCannotBeRestored() {
        LeadDO lead = lead("sales", "sales_self_sourced");
        lead.setOwnerUserId(null);
        assertFalse(policy.isEligible(lead));
        verifyNoInteractions(identityService);
    }

    private LeadDO lead(String identity, String source) {
        LeadDO lead = new LeadDO();
        lead.setOwnerUserId(20L); lead.setOwnerIdentity(identity); lead.setSourceType(source);
        return lead;
    }
}
