package cn.iocoder.yudao.module.zsjos.service.lead;

import java.util.Set;
import java.util.Map;

public record LeadInboxFilterQuery(Set<String> statuses, Set<String> assignmentStatuses,
                                   Set<String> handlingStages, boolean matchNone,
                                   Map<String, Set<String>> valuesByField) {

    public LeadInboxFilterQuery(Set<String> statuses, Set<String> assignmentStatuses, boolean matchNone) {
        this(statuses, assignmentStatuses, Set.of(), matchNone, Map.of());
    }

    public LeadInboxFilterQuery(Set<String> statuses, Set<String> assignmentStatuses, boolean matchNone,
                                Map<String, Set<String>> valuesByField) {
        this(statuses, assignmentStatuses, Set.of(), matchNone, valuesByField);
    }

    public Set<String> values(String field) {
        return valuesByField.getOrDefault(field, Set.of());
    }

    public boolean matches(String status, String assignmentStatus, String handlingStage) {
        return !matchNone
                && (statuses.isEmpty() || statuses.contains(status))
                && (assignmentStatuses.isEmpty() || assignmentStatuses.contains(assignmentStatus))
                && (handlingStages.isEmpty() || handlingStages.contains(handlingStage));
    }
}
