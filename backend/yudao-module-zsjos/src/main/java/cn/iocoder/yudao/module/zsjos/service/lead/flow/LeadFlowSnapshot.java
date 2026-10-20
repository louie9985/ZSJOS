package cn.iocoder.yudao.module.zsjos.service.lead.flow;

import java.util.List;
import java.util.Map;

/** Versioned business evidence, independent of current dictionary/user display values. */
public record LeadFlowSnapshot(int version, String operationKey, String title, String actorType,
                               Long actorId, String actorName, String result, String reason,
                               String relatedType, Long relatedId, String relatedNo,
                               List<Change> changes, List<String> coveredRecords) {
    public record Value(String state, String code, String text, String identityType, Long identityId) {
        public static Value text(Object value) {
            return value == null ? new Value("EMPTY", null, null, null, null)
                    : new Value("VALUE", value.toString(), value.toString(), null, null);
        }
    }
    private static boolean sameSelection(Value left, Value right) {
        if (left == null || right == null) return left == right;
        return java.util.Objects.equals(left.state(), right.state()) && java.util.Objects.equals(left.code(), right.code())
                && java.util.Objects.equals(left.identityType(), right.identityType()) && java.util.Objects.equals(left.identityId(), right.identityId());
    }
    /** Normalize old coverage keys while reading; immutable workflow evidence remains unchanged. */
    public static String canonicalRecordKey(String key) {
        return key != null && key.startsWith("aging:") ? "public-sea-pool:" + key.substring(6) : key;
    }

    public record Change(String field, String label, Value before, Value after) {}
    public static List<Change> diff(Map<String, Value> before, Map<String, Value> after, Map<String, String> labels) {
        return labels.entrySet().stream().filter(e -> !sameSelection(before.get(e.getKey()), after.get(e.getKey())))
                .map(e -> new Change(e.getKey(), e.getValue(), before.getOrDefault(e.getKey(),
                        new Value("UNRECORDED", null, null, null, null)), after.getOrDefault(e.getKey(),
                        new Value("UNRECORDED", null, null, null, null)))).toList();
    }
}
