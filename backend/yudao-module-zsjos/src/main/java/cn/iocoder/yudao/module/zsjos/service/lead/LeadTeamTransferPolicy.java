package cn.iocoder.yudao.module.zsjos.service.lead;

import cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.LeadDO;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import static cn.iocoder.yudao.module.zsjos.enums.LeadConstants.*;
import static cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants.*;
import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;

/** Resolves the persisted owner identity, proving recycled ownership from the latest transition. */
@Service
public class LeadTeamTransferPolicy {
    @Resource private cn.iocoder.yudao.module.zsjos.dal.mysql.lead.LeadAssignmentHistoryMapper historyMapper;

    public String resolveIdentity(LeadDO lead) {
        if (!ASSIGNMENT_RECYCLE_PENDING.equals(lead.getAssignmentStatus())) {
            if (known(lead.getOwnerIdentity())) return lead.getOwnerIdentity();
            throw exception(LEAD_RECYCLED_IDENTITY_UNKNOWN);
        }
        Long source = lead.getRecycleSourceOwnerUserId();
        if (source == null || lead.getOwnerUserId() != null) throw exception(LEAD_RECYCLED_IDENTITY_UNKNOWN);
        var rows = historyMapper.selectByLeadId(lead.getId());
        // The latest transition must prove this recycle. Never search past a newer release,
        // recycle or ownership transition, even when an older snapshot names the same employee.
        if (rows.isEmpty()) throw exception(LEAD_RECYCLED_IDENTITY_UNKNOWN);
        var recycle = rows.getFirst();
        if (!ACTION_RECYCLE.equals(recycle.getActionType())
                || !source.equals(recycle.getFromOwnerUserId()) || recycle.getToOwnerUserId() != null)
            throw exception(LEAD_RECYCLED_IDENTITY_UNKNOWN);
        String evidence = recycle.getOwnerIdentitySnapshot();
        if (!known(evidence)) {
            if (evidence != null || rows.size() < 2) throw exception(LEAD_RECYCLED_IDENTITY_UNKNOWN);
            var owner = rows.get(1);
            if (owner.getActionType() == null || !java.util.Set.of(ACTION_ACCEPT, ACTION_CLAIM, ACTION_TRANSFER).contains(owner.getActionType())
                    || !source.equals(owner.getToOwnerUserId()) || !known(owner.getOwnerIdentitySnapshot()))
                throw exception(LEAD_RECYCLED_IDENTITY_UNKNOWN);
            evidence = owner.getOwnerIdentitySnapshot();
        }
        if (lead.getOwnerIdentity() != null && !evidence.equals(lead.getOwnerIdentity()))
            throw exception(LEAD_RECYCLED_IDENTITY_UNKNOWN);
        return evidence;
    }

    private static boolean known(String identity) {
        return OWNER_SALES.equals(identity);
    }
}
