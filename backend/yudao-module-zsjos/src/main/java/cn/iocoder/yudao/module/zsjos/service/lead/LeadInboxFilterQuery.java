package cn.iocoder.yudao.module.zsjos.service.lead;

import java.util.Set;

public record LeadInboxFilterQuery(Set<String> statuses, Set<String> assignmentStatuses, boolean matchNone) {

    public boolean matches(String status, String assignmentStatus) {
        return !matchNone
                && (statuses.isEmpty() || statuses.contains(status))
                && (assignmentStatuses.isEmpty() || assignmentStatuses.contains(assignmentStatus));
    }
}
