package cn.iocoder.yudao.module.zsjos.service.lead;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.event.BusinessEventDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.LeadDO;
import org.junit.jupiter.api.Test;
import java.time.LocalDateTime;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class LeadRemarkHistoryServiceTest {
    private final LeadDO lead = lead();

    @Test void initialAndRepeatedAppendsRemainSeparateAndSorted() {
        var result = LeadRemarkHistoryService.project(lead, List.of(append(3, "B"), append(2, "B")), "author", false);
        assertEquals(List.of("A", "B", "B"), result.items().stream().map(r -> r.content()).toList());
        assertEquals("event:2", result.items().get(1).id());
        assertEquals("snapshot", result.items().get(1).operatorName());
        assertFalse(result.incomplete()); assertFalse(result.hasLegacy());
    }

    @Test void legacySnapshotsRecoverTextWithoutInventingAttribution() {
        lead.setRemark("C");
        var result = LeadRemarkHistoryService.project(lead,
                List.of(legacy(1, "A"), legacy(2, "B"), legacy(3, "B"), append(4, "D")), "author", false);
        assertEquals(List.of("A", "B", "C", "D"), result.items().stream().map(r -> r.content()).toList());
        result.items().stream().filter(r -> r.kind().equals("legacy")).forEach(r -> {
            assertNull(r.occurredAt()); assertNull(r.operatorName());
        });
        assertEquals(2, result.evidence().get("legacy:event:2").size());
        assertTrue(result.hasLegacy()); assertFalse(result.incomplete());
    }

    @Test void malformedAndMissingEvidenceIsExplicitWhileAvailableContentSurvives() {
        var result = LeadRemarkHistoryService.project(lead, List.of(event(1, "{broken"), event(2, "{}"), append(3, "B")), null, true);
        assertTrue(result.incomplete()); assertTrue(result.hasLegacy());
        assertEquals(List.of("A", "B"), result.items().stream().map(r -> r.content()).toList());
        assertNull(result.items().get(1).operatorName());
    }

    @Test void blankAppendDoesNotCreateEntryAndMaskingDoesNotExposeSnapshot() {
        var result = LeadRemarkHistoryService.project(lead, List.of(append(1, ""), append(2, "B")), "masked", true);
        assertEquals(2, result.items().size()); assertFalse(result.incomplete());
        assertEquals("masked", result.items().get(1).operatorName());
        assertNull(LeadRemarkHistoryService.appendedRemark(legacy(3, "old")));
    }

    @Test void missingInitialAndNullLegacyAreNotInvented() {
        lead.setRemark(null);
        assertTrue(LeadRemarkHistoryService.project(lead, List.of(), null, false).items().isEmpty());
        var result = LeadRemarkHistoryService.project(lead, List.of(event(1, "{\"before\":{\"remark\":null}}")), null, false);
        assertTrue(result.items().isEmpty()); assertFalse(result.incomplete());
    }

    @Test void responseRecordUsesEpochMillisAndOmitsInternalEvidence() {
        var result = LeadRemarkHistoryService.project(lead, List.of(append(1, "B")), "author", false);
        long time = lead.getSubmittedAt().atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli();
        String json = JsonUtils.toJsonString(result.items());
        assertTrue(json.contains("\"occurredAt\":" + time));
        assertFalse(json.contains("submitterId")); assertFalse(json.contains("requestDigest"));
    }

    private static LeadDO lead() {
        LeadDO row = new LeadDO(); row.setId(9L); row.setRemark("A"); row.setSubmittedAt(LocalDateTime.of(2026, 9, 6, 10, 0)); return row;
    }
    private static BusinessEventDO append(long id, String text) {
        return event(id, JsonUtils.toJsonString(new LeadSupplementSnapshot(Map.of(), LeadSupplementSnapshot.MODE,
                text, "system_user", 1L, "snapshot", "digest")));
    }
    private static BusinessEventDO legacy(long id, String text) {
        return event(id, JsonUtils.toJsonString(Map.of("before", Map.of("remark", text))));
    }
    private static BusinessEventDO event(long id, String json) {
        BusinessEventDO row = new BusinessEventDO(); row.setId(id); row.setEventType(LeadSupplementSnapshot.EVENT);
        row.setRelatedObjectRefs(json); row.setOccurredAt(LocalDateTime.of(2026, 9, 6, 11, 0).plusSeconds(id)); return row;
    }
}
