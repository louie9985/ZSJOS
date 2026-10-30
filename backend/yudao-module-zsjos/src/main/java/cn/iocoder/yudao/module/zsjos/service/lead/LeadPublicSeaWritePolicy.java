package cn.iocoder.yudao.module.zsjos.service.lead;

import cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.LeadPublicSeaPoolCycleDO;
import java.util.Objects;
import java.util.Set;
import static cn.iocoder.yudao.module.zsjos.enums.LeadConstants.*;

/** Writing a record does not transfer formal ownership or personal task responsibility. */
final class LeadPublicSeaWritePolicy {
    private LeadPublicSeaWritePolicy() {}

    static boolean canFollow(LeadPublicSeaPoolCycleDO cycle, Long userId) {
        if (cycle == null || cycle.getStatus() == null || userId == null) return false;
        return Objects.equals(userId, cycle.getOriginalOwnerUserId())
                && Set.of(PUBLIC_SEA_POOL_WAITING_ASSIGNMENT, PUBLIC_SEA_POOL_ASSIGNED).contains(cycle.getStatus())
                || Objects.equals(userId, cycle.getCollaboratorUserId())
                && Set.of(PUBLIC_SEA_POOL_ASSIGNED, PUBLIC_SEA_POOL_DEAL_PENDING).contains(cycle.getStatus());
    }

    static boolean canEdit(LeadPublicSeaPoolCycleDO cycle, Long userId) {
        return cycle != null && cycle.getStatus() != null && userId != null
                && Set.of(PUBLIC_SEA_POOL_WAITING_ASSIGNMENT, PUBLIC_SEA_POOL_ASSIGNED, PUBLIC_SEA_POOL_DEAL_PENDING).contains(cycle.getStatus())
                && (Objects.equals(userId, cycle.getOriginalOwnerUserId())
                || Objects.equals(userId, cycle.getCollaboratorUserId()));
    }
}
