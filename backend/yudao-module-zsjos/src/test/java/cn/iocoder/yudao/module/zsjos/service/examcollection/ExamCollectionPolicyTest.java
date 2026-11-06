package cn.iocoder.yudao.module.zsjos.service.examcollection;

import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;
import static cn.iocoder.yudao.module.zsjos.service.examcollection.ExamCollectionPolicy.*;

class ExamCollectionPolicyTest {
    @Test void matchesOnlySuppliedRosterAndNormalizesSuffix() {
        var roster = List.of(new Candidate(10, "syntheticX"));
        assertEquals(new Match(MatchStatus.MATCHED, 10L), match(" syntheticx ", roster, null, false));
        assertEquals(MatchStatus.UNMATCHED, match("other", roster, null, false).status());
        assertEquals(MatchStatus.UNMATCHED, match(" ", roster, null, false).status());
    }
    @Test void duplicateRowsDoNotCreateAmbiguityButDifferentServicesDo() {
        assertEquals(MatchStatus.MATCHED, match("A", List.of(new Candidate(10, "A"), new Candidate(10, "A")), null, false).status());
        assertEquals(MatchStatus.AMBIGUOUS, match("A", List.of(new Candidate(10, "A"), new Candidate(11, "A")), null, false).status());
    }
    @Test void manualOutsideRosterBindingPersistsAcrossRepeatedSubmissions() {
        assertEquals(new Match(MatchStatus.MATCHED, 99L), match("A", List.of(), 99L, true));
        assertEquals(new Match(MatchStatus.MATCHED, 99L), match("A", List.of(new Candidate(10, "A")), 99L, true));
        assertEquals(MatchStatus.INVALID_BINDING, match("A", List.of(new Candidate(10, "A")), 99L, false).status());
    }
    @Test void delayedResolutionCannotOverwriteNewerSubmission() {
        assertTrue(shouldApply(1, null));
        assertTrue(shouldApply(2, 1L));
        assertFalse(shouldApply(2, 2L));
        assertFalse(shouldApply(1, 2L));
        assertThrows(IllegalArgumentException.class, () -> shouldApply(0, null));
    }
    @Test void countsServicesNotRepeatedSubmissionsOrAdditionalPeople() {
        var roster = List.of(10L, 11L);
        assertEquals(1, collected(roster, List.of(10L, 10L, 99L)));
        assertFalse(plannerComplete(roster, List.of(10L, 99L)));
        assertTrue(plannerComplete(roster, List.of(10L, 11L, 99L)));
        assertTrue(plannerComplete(List.of(), List.of()));
    }
    @Test void updatesSelectedFieldsWithoutMutatingOriginalOrUnselectedMaterials() {
        var original = Map.of("mobile", "old", "portrait", "previous-photo", "diploma_images", "old-diploma");
        var merged = merge(original, Map.of("mobile", "new", "portrait", "new-photo"), Set.of("mobile", "portrait"));
        assertEquals("new", merged.get("mobile"));
        assertEquals("new-photo", merged.get("portrait"));
        assertEquals("old-diploma", merged.get("diploma_images"));
        assertEquals("old", original.get("mobile"));
        assertThrows(UnsupportedOperationException.class, () -> merged.put("mobile", "other"));
    }
    @Test void rejectsUnselectedFieldsAndIncompleteReplacement() {
        assertThrows(IllegalArgumentException.class, () -> merge(Map.of(), Map.of("gift_shipping_address", "x"), Set.of("name")));
        assertThrows(IllegalArgumentException.class, () -> merge(Map.of(), Map.of("name", "x"), Set.of("name", "mobile")));
    }
}
