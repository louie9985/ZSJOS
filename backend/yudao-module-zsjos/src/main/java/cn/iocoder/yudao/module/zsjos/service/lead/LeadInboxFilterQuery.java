package cn.iocoder.yudao.module.zsjos.service.lead;

import java.util.Set;
import java.util.Map;

public record LeadInboxFilterQuery(Set<String> statuses, Set<String> assignmentStatuses, boolean matchNone,
                                   Map<String, Set<String>> valuesByField) {

    public LeadInboxFilterQuery(Set<String> statuses, Set<String> assignmentStatuses, boolean matchNone) {
        this(statuses, assignmentStatuses, matchNone, Map.of());
    }

    public Set<String> values(String field) {
        return valuesByField.getOrDefault(field, Set.of());
    }

    public boolean matches(String status, String assignmentStatus) {
        return !matchNone
                && (statuses.isEmpty() || statuses.contains(status))
                && (assignmentStatuses.isEmpty() || assignmentStatuses.contains(assignmentStatus));
    }
}
