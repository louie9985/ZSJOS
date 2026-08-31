package cn.iocoder.yudao.module.zsjos.service.lead;

import cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.LeadDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.OpportunityDO;

import static cn.iocoder.yudao.module.zsjos.enums.LeadConstants.*;

/** Keeps qualification, sales progress, and operational control as independent projections. */
public final class LeadStateProjection {

    private LeadStateProjection() {
    }

    public static String qualification(LeadDO lead) {
        if (STATUS_INVALID.equals(lead.getStatus())) return QUALIFICATION_INVALID;
        if (STATUS_VALID.equals(lead.getStatus()) || STATUS_WON.equals(lead.getStatus())) return QUALIFICATION_VALID;
        return QUALIFICATION_PENDING;
    }

    public static String followUp(LeadDO lead, OpportunityDO opportunity) {
        if (STATUS_INVALID.equals(lead.getStatus())) return null;
        if (opportunity != null) {
            if (OPPORTUNITY_STATUS_WON.equals(opportunity.getStatus())) return FOLLOW_UP_WON;
            if (OPPORTUNITY_STATUS_DEAL_PENDING_APPROVAL.equals(opportunity.getStatus())) {
                return FOLLOW_UP_DEAL_PENDING_APPROVAL;
            }
        }
        if (STATUS_VALID.equals(lead.getStatus())) return FOLLOW_UP_FOLLOWING;
        if (STATUS_SUSPENDED.equals(lead.getStatus()) && lead.getQualificationDeadlineAt() != null) {
            return FOLLOW_UP_FOLLOWING;
        }
        if (STATUS_SUBMITTED.equals(lead.getStatus()) && ASSIGNMENT_OWNED.equals(lead.getAssignmentStatus())) {
            return lead.getQualificationDeadlineAt() == null ? FOLLOW_UP_FIRST_PENDING : FOLLOW_UP_FOLLOWING;
        }
        return null;
    }

    public static String operational(LeadDO lead) {
        return STATUS_SUSPENDED.equals(lead.getStatus()) ? OPERATIONAL_SUSPENDED : OPERATIONAL_ACTIVE;
    }
}
