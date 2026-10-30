package cn.iocoder.yudao.module.zsjos.service.lead;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.LeadDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.lead.LeadMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.lead.LeadPublicSeaPoolCompatibilityRecordMapper;
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

    @Resource private LeadPublicSeaPoolService publicSeaPoolService;
    @Resource private LeadPublicSeaPoolCompatibilityRecordMapper publicSeaPoolCompatibilityRecordMapper;
    @Resource private LeadMapper leadMapper;
    @Resource private cn.iocoder.yudao.module.zsjos.dal.mysql.lead.LeadPublicSeaPoolCycleMapper cycleMapper;

    public void requireCanWriteForUpdate(LeadDO lead, Long userId, boolean basicInfo) {
        var cycle = cycleMapper.selectActiveByLeadIdForUpdate(lead.getId(), TenantContextHolder.getRequiredTenantId());
        if (cycle != null) {
            if (publicSeaPoolCompatibilityRecordMapper.selectByLeadIdForUpdate(lead.getId(),
                    TenantContextHolder.getRequiredTenantId()) != null) throw exception(LEAD_COLLABORATION_POOL_CONFLICT);
            if (!(basicInfo ? LeadPublicSeaWritePolicy.canEdit(cycle, userId)
                    : LeadPublicSeaWritePolicy.canFollow(cycle, userId))) throw exception(LEAD_PERMISSION_DENIED);
        } else if (basicInfo) {
            if (userId == null || !Objects.equals(userId, lead.getOwnerUserId())) throw exception(LEAD_PERMISSION_DENIED);
        } else {
            requireCanOperateForUpdate(lead, userId);
        }
    }


    public OperationContext requireCanOperateForUpdate(LeadDO lead, Long operatorUserId) {
        if (operatorUserId == null) throw exception(LEAD_PERMISSION_DENIED);
        var cycle = publicSeaPoolService.getActiveCycle(lead.getId());
        var manual = publicSeaPoolCompatibilityRecordMapper.selectByLeadIdForUpdate(
                lead.getId(), TenantContextHolder.getRequiredTenantId());
        if (cycle != null && manual != null) {
            throw exception(LEAD_COLLABORATION_POOL_CONFLICT);
        }
        if (cycle != null) {
            publicSeaPoolService.requireCanOperateForUpdate(lead.getId(), lead.getOwnerUserId(), operatorUserId);
            return new OperationContext(true, "public_sea_pool", lead.getOwnerUserId());
        }
        if (manual != null && Objects.equals(manual.getOwnerUserId(), lead.getOwnerUserId())
                && Objects.equals(manual.getCollaboratorUserId(), operatorUserId)) {
            return new OperationContext(true, "public_sea_pool", lead.getOwnerUserId());
        }
        if (manual == null && Objects.equals(lead.getOwnerUserId(), operatorUserId)) {
            return OperationContext.owner(lead.getOwnerUserId());
        }
        throw exception(LEAD_PERMISSION_DENIED);
    }

    /**
     * Entry to a first-purchase order is stricter than follow-up collaboration: only the
     * formal owner may submit. A public-sea collaborator must use the transfer boundary first.
     */
    public void requireCanEnterDealForUpdate(LeadDO lead, Long operatorUserId) {
        if (Objects.equals(lead.getOwnerUserId(), operatorUserId)) {
            var cycle = publicSeaPoolService.getActiveCycle(lead.getId());
            var manual = publicSeaPoolCompatibilityRecordMapper.selectByLeadIdForUpdate(
                    lead.getId(), TenantContextHolder.getRequiredTenantId());
            if (cycle != null || manual != null) {
                throw exception(cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants.SALES_ORDER_ENTRY_REQUIRES_POOL_EXIT);
            }
            return;
        }
        throw exception(cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants.SALES_ORDER_ENTRY_REQUIRES_POOL_EXIT);
    }

}
