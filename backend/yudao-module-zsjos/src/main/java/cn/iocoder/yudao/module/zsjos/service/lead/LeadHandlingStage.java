package cn.iocoder.yudao.module.zsjos.service.lead;

import cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.LeadDO;

import static cn.iocoder.yudao.module.zsjos.enums.LeadConstants.*;

public final class LeadHandlingStage {
    public static final String FIRST_FOLLOW_PENDING = "first_follow_pending";
    public static final String QUALIFICATION_PENDING = "qualification_pending";
    public static final String PENDING_CLAIM = "pending_claim";

    private LeadHandlingStage() {
    }

    public static String resolve(LeadDO lead) {
        if (STATUS_SUSPENDED.equals(lead.getStatus())) return STATUS_SUSPENDED;
        if (ASSIGNMENT_RECYCLE_PENDING.equals(lead.getAssignmentStatus())) return ASSIGNMENT_RECYCLE_PENDING;
        if (ASSIGNMENT_PUBLIC_POOL.equals(lead.getAssignmentStatus())) return PENDING_CLAIM;
        if (ASSIGNMENT_PENDING.equals(lead.getAssignmentStatus())) return ASSIGNMENT_PENDING;
        if (ASSIGNMENT_UNASSIGNED.equals(lead.getAssignmentStatus())) return ASSIGNMENT_UNASSIGNED;
        if (STATUS_SUBMITTED.equals(lead.getStatus()) && ASSIGNMENT_OWNED.equals(lead.getAssignmentStatus())) {
            return lead.getQualificationDeadlineAt() == null ? FIRST_FOLLOW_PENDING : QUALIFICATION_PENDING;
        }
        return lead.getStatus();
    }
}
