package cn.iocoder.yudao.module.zsjos.service.lead;

import java.util.Set;

import static cn.iocoder.yudao.module.zsjos.enums.LeadConstants.*;

/** Canonical simple-status filters for the unified Lead list. */
public record LeadSimpleStatusQuery(Set<String> leadStatuses,
                                    Set<String> assignmentStatuses,
                                    Set<String> handlingStages,
                                    Set<String> requiredOpportunityStatuses,
                                    Set<String> excludedOpportunityStatuses) {

    public static final String VALIDATION_PATTERN =
            "all|first_follow_pending|qualification_pending|"
                    + "following|deal_pending_approval|won|invalid|closed|suspended";

    private static final LeadSimpleStatusQuery ALL = query(Set.of(), Set.of(), Set.of(), Set.of(), Set.of());

    public static LeadSimpleStatusQuery resolve(String simpleStatus) {
        if (simpleStatus == null || simpleStatus.isBlank() || "all".equals(simpleStatus)) return ALL;
        return switch (simpleStatus) {
            case LeadHandlingStage.FIRST_FOLLOW_PENDING -> query(Set.of(STATUS_SUBMITTED), Set.of(ASSIGNMENT_OWNED),
                    Set.of(LeadHandlingStage.FIRST_FOLLOW_PENDING), Set.of(), Set.of());
            case LeadHandlingStage.QUALIFICATION_PENDING -> query(Set.of(STATUS_SUBMITTED), Set.of(ASSIGNMENT_OWNED),
                    Set.of(LeadHandlingStage.QUALIFICATION_PENDING), Set.of(), Set.of());
            case FOLLOW_UP_FOLLOWING -> query(Set.of(STATUS_VALID), Set.of(), Set.of(), Set.of(),
                    Set.of(OPPORTUNITY_STATUS_DEAL_PENDING_APPROVAL, OPPORTUNITY_STATUS_WON));
            case FOLLOW_UP_DEAL_PENDING_APPROVAL -> query(Set.of(STATUS_VALID), Set.of(), Set.of(),
                    Set.of(OPPORTUNITY_STATUS_DEAL_PENDING_APPROVAL), Set.of());
            case STATUS_WON -> query(Set.of(STATUS_WON), Set.of(), Set.of(), Set.of(), Set.of());
            case STATUS_INVALID -> query(Set.of(STATUS_INVALID), Set.of(), Set.of(), Set.of(), Set.of());
            case STATUS_CLOSED -> query(Set.of(STATUS_CLOSED), Set.of(), Set.of(), Set.of(), Set.of());
            case STATUS_SUSPENDED -> query(Set.of(STATUS_SUSPENDED), Set.of(), Set.of(), Set.of(), Set.of());
            default -> throw new IllegalArgumentException("Unsupported Lead simple status: " + simpleStatus);
        };
    }

    private static LeadSimpleStatusQuery query(Set<String> leadStatuses, Set<String> assignmentStatuses,
                                               Set<String> handlingStages, Set<String> requiredOpportunityStatuses,
                                               Set<String> excludedOpportunityStatuses) {
        return new LeadSimpleStatusQuery(leadStatuses, assignmentStatuses, handlingStages,
                requiredOpportunityStatuses, excludedOpportunityStatuses);
    }
}
