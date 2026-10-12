package cn.iocoder.yudao.module.zsjos.service.lead;

import cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.LeadDO;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import static cn.iocoder.yudao.module.zsjos.enums.LeadConstants.*;

@Service
public class LeadRestoreOwnerPolicy {
    @Resource private LeadAssignmentService assignmentService;
    @Resource private LeadSubmissionIdentityService identityService;

    public boolean isEligible(LeadDO lead) {
        if (lead.getOwnerUserId() == null) return false;
        // Restoring existing education ownership does not accept a new lead: current acceptance
        // permission and partner assignment relations must not invalidate the persisted ownership.
        // Operation permission and manager scope remain separate from retained-owner eligibility.
        if (OWNER_EDUCATION.equals(lead.getOwnerIdentity())) {
            boolean supportedSource = SOURCE_EDUCATION_SELF.equals(lead.getSourceType())
                    || SOURCE_INTERNAL_NEW_MEDIA.equals(lead.getSourceType())
                    && SOURCE_EDUCATION_SELF.equals(lead.getSubmissionSourceType())
                    && DISPATCH_SELF.equals(lead.getDispatchMode())
                    || SOURCE_PARTNER.equals(lead.getSourceType())
                    && DISPATCH_SPECIFIED.equals(lead.getDispatchMode());
            return supportedSource && identityService.isEligibleEducationOwner(lead.getOwnerUserId());
        }
        return assignmentService.isEligibleSalesUser(lead.getOwnerUserId());
    }
}
