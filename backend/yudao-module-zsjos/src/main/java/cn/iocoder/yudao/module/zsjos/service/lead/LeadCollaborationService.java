package cn.iocoder.yudao.module.zsjos.service.lead;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.LeadDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.lead.LeadMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.lead.LeadPublicSeaRecordMapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.util.Objects;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants.LEAD_COLLABORATION_POOL_CONFLICT;
import static cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants.LEAD_PERMISSION_DENIED;

/** Coordinates owner-preserving collaboration and the explicit transfer boundary. */
@Service
public class LeadCollaborationService {

    public record OperationContext(boolean collaborator, String poolType, Long originalOwnerUserId) {
        static OperationContext owner(Long ownerUserId) {
            return new OperationContext(false, null, ownerUserId);
        }
    }

    @Resource private LeadAgingPoolService agingPoolService;
    @Resource private LeadPublicSeaRecordMapper publicSeaRecordMapper;
    @Resource private LeadMapper leadMapper;

    public OperationContext requireCanOperateForUpdate(LeadDO lead, Long operatorUserId) {
        if (Objects.equals(lead.getOwnerUserId(), operatorUserId)) {
            lockCollaborationRows(lead.getId(), operatorUserId, lead.getOwnerUserId());
            return OperationContext.owner(lead.getOwnerUserId());
        }
        var cycle = agingPoolService.getActiveCycle(lead.getId());
        var manual = publicSeaRecordMapper.selectByLeadIdForUpdate(
                lead.getId(), TenantContextHolder.getRequiredTenantId());
        if (cycle != null && manual != null) {
            throw exception(LEAD_COLLABORATION_POOL_CONFLICT);
        }
        if (cycle != null) {
            agingPoolService.requireCanOperateForUpdate(lead.getId(), lead.getOwnerUserId(), operatorUserId);
            return new OperationContext(true, "aging_pool", lead.getOwnerUserId());
        }
        if (manual != null && Objects.equals(manual.getOwnerUserId(), lead.getOwnerUserId())
                && Objects.equals(manual.getCollaboratorUserId(), operatorUserId)) {
            return new OperationContext(true, "manual_public_sea", lead.getOwnerUserId());
        }
        throw exception(LEAD_PERMISSION_DENIED);
    }

    /**
     * Entry to a first-purchase order is stricter than follow-up collaboration: only the
     * formal owner may submit. A public-sea collaborator must use the transfer boundary first.
     */
    public void requireCanEnterDealForUpdate(LeadDO lead, Long operatorUserId) {
        if (Objects.equals(lead.getOwnerUserId(), operatorUserId)) {
            lockCollaborationRows(lead.getId(), operatorUserId, lead.getOwnerUserId());
            return;
        }
        throw exception(cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants.SALES_ORDER_ENTRY_REQUIRES_TRANSFER);
    }

    private void lockCollaborationRows(Long leadId, Long operatorUserId, Long ownerUserId) {
        var cycle = agingPoolService.getActiveCycle(leadId);
        if (cycle != null) {
            agingPoolService.requireCanOperateForUpdate(leadId, ownerUserId, operatorUserId);
        }
        var manual = publicSeaRecordMapper.selectByLeadIdForUpdate(
                leadId, TenantContextHolder.getRequiredTenantId());
        if (cycle != null && manual != null) throw exception(LEAD_COLLABORATION_POOL_CONFLICT);
    }
}
