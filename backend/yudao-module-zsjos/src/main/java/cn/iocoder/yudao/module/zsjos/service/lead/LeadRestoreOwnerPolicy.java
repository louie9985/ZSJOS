package cn.iocoder.yudao.module.zsjos.service.lead;

import cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.LeadDO;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

@Service
public class LeadRestoreOwnerPolicy {
    @Resource private LeadTeamTransferPolicy teamTransferPolicy;
    @Resource private LeadSubmissionIdentityService identityService;

    public boolean isEligible(LeadDO lead) {
        if (lead.getOwnerUserId() == null) return false;
        // Unknown persisted identity fails closed. Restoring existing ownership does not accept a new lead:
        // current acceptance permission and partner assignment relations must not invalidate it.
        teamTransferPolicy.resolveIdentity(lead);
        return identityService.isEligibleRetainedOwner(lead.getOwnerUserId());
    }
}
