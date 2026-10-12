package cn.iocoder.yudao.module.zsjos.service.lead;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static cn.iocoder.yudao.module.zsjos.service.lead.LeadSourceInterpretation.*;

class LeadSourceInterpretationTest {
    @Test void linkedWritesRequireSourceAndContributionToMatchTheProvider() {
        var lead = new cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.LeadDO();
        lead.setSourceType("internal_new_media");
        lead.setSubmissionSourceType("sales_self_sourced");
        lead.setSourceProviderRecorded(true);
        lead.setSourceProviderUserId(26L);
        lead.setProviderOwnerType("system_user");
        lead.setProviderOwnerId(26L);
        lead.setSourceUserId(60L);
        lead.setContributionUserIdSnapshot(60L);
        assertFalse(hasConsistentWriteAttribution(lead));
        lead.setSourceUserId(26L);
        assertFalse(hasConsistentWriteAttribution(lead));
        lead.setContributionUserIdSnapshot(26L);
        lead.setContributionUserNameSnapshot("历史姓名");
        assertTrue(hasConsistentWriteAttribution(lead));
        lead.setSubmissionSourceType("education_self_sourced");
        assertTrue(hasConsistentWriteAttribution(lead));
    }
    @Test void bothSelfEntriesRequireAnExplicitConsistentDecision() {
        for (String source : new String[]{"sales_self_sourced", "education_self_sourced"}) {
            assertEquals(State.UNVERIFIED, resolve(source, null, false, null, null, null).state());
            assertEquals(State.UNVERIFIED, resolve(source, null, false, 17L, "system_user", 17L).state());
            assertEquals(State.UNLINKED, resolve(source, source, true, null, null, null).state());
            assertEquals(State.LINKED, resolve("internal_new_media", source, true, 17L, "system_user", 17L).state());
            assertEquals(Reason.PROVIDER_MISMATCH, resolve(source, null, true, null, "system_user", 17L).reason());
            assertEquals(Reason.PROVIDER_MISMATCH, resolve("internal_new_media", source, true, 17L, "system_user", 18L).reason());
        }
    }
    @Test void nativeSourcesDoNotRequireSelfEntryConfirmationButExplicitConflictsAreDetected() {
        assertEquals(State.NOT_APPLICABLE, resolve("partner", null, false, null, "partner", 17L).state());
        assertEquals(State.NOT_APPLICABLE, resolve("internal_new_media", null, false, null, "system_user", 17L).state());
        assertEquals(State.UNVERIFIED, resolve("internal_new_media", null, true, 400L, "system_user", 527L).state());
        assertEquals(State.UNVERIFIED, resolve("internal_new_media", "sales_self_sourced", false, null, null, null).state());
        assertEquals(Reason.SOURCE_MISMATCH, resolve("internal_new_media", "education_self_sourced", true, null, null, null).reason());
    }
}
