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
        // Education self-sourced ownership uses the same eligibility as its creation entry point.
        // This only validates the retained owner; operation permission and manager scope remain separate.
        if (OWNER_EDUCATION.equals(lead.getOwnerIdentity())) {
            return SOURCE_EDUCATION_SELF.equals(lead.getSourceType())
                    && identityService.isEligibleEducationOwner(lead.getOwnerUserId());
        }
        return assignmentService.isEligibleSalesUser(lead.getOwnerUserId());
    }
}
