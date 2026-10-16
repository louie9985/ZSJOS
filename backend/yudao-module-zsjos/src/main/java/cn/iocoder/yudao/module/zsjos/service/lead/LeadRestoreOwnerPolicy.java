package cn.iocoder.yudao.module.zsjos.service.lead;

import cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.LeadDO;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import static cn.iocoder.yudao.module.zsjos.enums.LeadConstants.*;

@Service
public class LeadRestoreOwnerPolicy {
    @Resource private LeadTeamTransferPolicy teamTransferPolicy;
    @Resource private LeadAssignmentService assignmentService;
    @Resource private LeadSubmissionIdentityService identityService;

    public boolean isEligible(LeadDO lead) {
        if (lead.getOwnerUserId() == null) return false;
        // Restoring existing education ownership does not accept a new lead: current acceptance
        // permission and partner assignment relations must not invalidate the persisted ownership.
        // Operation permission and manager scope remain separate from retained-owner eligibility.
        if (OWNER_EDUCATION.equals(teamTransferPolicy.resolveIdentity(lead))) {
            return EducationOwnershipPolicy.supports(lead) && identityService.isEligibleEducationOwner(lead.getOwnerUserId());
        }
        return assignmentService.isEligibleSalesUser(lead.getOwnerUserId());
    }
}
