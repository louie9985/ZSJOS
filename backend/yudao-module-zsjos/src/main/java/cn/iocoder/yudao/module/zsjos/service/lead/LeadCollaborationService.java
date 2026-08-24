package cn.iocoder.yudao.module.zsjos.service.lead;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.LeadAssignmentHistoryDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.LeadDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.OpportunityDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.lead.LeadAssignmentHistoryMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.lead.LeadMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.lead.LeadPublicSeaRecordMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.lead.OpportunityMapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Objects;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.zsjos.enums.LeadConstants.ACTION_TRANSFER;
import static cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants.LEAD_COLLABORATION_POOL_CONFLICT;
import static cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants.LEAD_PERMISSION_DENIED;

/** Coordinates owner-preserving collaboration and the explicit submit-time ownership transfer. */
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
    @Resource private OpportunityMapper opportunityMapper;
    @Resource private LeadAssignmentHistoryMapper assignmentHistoryMapper;
    @Resource private LeadLifecycleTaskService lifecycleTaskService;

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

    public void transferOnOrderSubmission(LeadDO lead, OpportunityDO opportunity,
                                          OperationContext context, Long operatorUserId,
                                          LocalDateTime transferredAt) {
        if (!context.collaborator()) return;
        if (!Objects.equals(lead.getOwnerUserId(), context.originalOwnerUserId())) {
            throw exception(LEAD_PERMISSION_DENIED);
        }
        String reason = "公海协同销售提交订单，正式归属转移";
        if ("aging_pool".equals(context.poolType())) {
            agingPoolService.terminateForOwnerTransfer(lead.getId(), operatorUserId, operatorUserId,
                    transferredAt, reason);
        } else {
            publicSeaRecordMapper.deleteByLeadId(lead.getId());
        }

        LeadAssignmentHistoryDO history = new LeadAssignmentHistoryDO();
        history.setLeadId(lead.getId());
        history.setActionType(ACTION_TRANSFER);
        history.setFromOwnerUserId(context.originalOwnerUserId());
        history.setToOwnerUserId(operatorUserId);
        history.setOperatorUserId(operatorUserId);
        history.setReason(reason);
        history.setOccurredAt(transferredAt);
        assignmentHistoryMapper.insert(history);

        lead.setOwnerUserId(operatorUserId);
        lead.setOwnershipStartedAt(transferredAt);
        lead.setCurrentAssignmentHistoryId(history.getId());
        leadMapper.updateById(lead);
        opportunity.setOwnerUserId(operatorUserId);
        opportunityMapper.updateById(opportunity);
        lifecycleTaskService.reassignPendingSalesTasks(lead.getId(), operatorUserId);
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
