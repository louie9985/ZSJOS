package cn.iocoder.yudao.module.zsjos.service.lead;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.assignment.LeadAssignmentUserRespVO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.LeadDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.lead.LeadMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.order.SalesOrderMapper;
import cn.iocoder.yudao.module.zsjos.framework.permission.ZsjosPermission;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.zsjos.enums.LeadConstants.*;
import static cn.iocoder.yudao.module.zsjos.enums.SalesOrderConstants.ACTIVE_ORDER_STATUSES;
import static cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants.*;

@Service
public class LeadOwnerCommandService {
    @Resource private LeadMapper leadMapper;
    @Resource private SalesOrderMapper salesOrderMapper;
    @Resource private LeadDispatchService dispatchService;
    @Resource private LeadPublicSeaPoolService publicSeaPoolService;
    @Resource private LeadAssignmentService assignmentService;
    @Resource private cn.iocoder.yudao.module.zsjos.service.sales.SalesDepartmentBoundaryService salesBoundary;

    /** Candidate owners without a specific lead, limited to the requester's sales boundary like the per-lead list. */
    public List<LeadAssignmentUserRespVO> getTransferCandidates(Long ownerUserId) {
        var candidates = assignmentService.getEligibleSalesUsers();
        var allowed = salesBoundary.filterTransferTargets(ownerUserId, candidates.stream().map(LeadAssignmentUserRespVO::getId).toList());
        return candidates.stream().filter(user -> allowed.contains(user.getId())).toList();
    }

    @ZsjosPermission(bizType = "lead", bizId = "#leadId", action = "owner-transfer")
    public List<LeadAssignmentUserRespVO> getTransferCandidates(Long leadId, Long ownerUserId) {
        LeadDO lead = leadMapper.selectById(leadId);
        if (lead == null) throw exception(LEAD_NOT_EXISTS);
        if (!ownerUserId.equals(lead.getOwnerUserId())) throw exception(LEAD_PERMISSION_DENIED);
        if (STATUS_WON.equals(lead.getStatus()) || STATUS_CLOSED.equals(lead.getStatus())) throw exception(LEAD_PERMISSION_DENIED);
        if (salesOrderMapper.selectActiveByLeadId(leadId, ACTIVE_ORDER_STATUSES) != null) throw exception(LEAD_OWNER_TRANSFER_DEAL_ACTIVE);
        String source = lead.getSubmissionSourceType() == null ? lead.getSourceType() : lead.getSubmissionSourceType();
        var candidates = isSelfSourced(source) ? assignmentService.getEligibleSalesUsers() : assignmentService.getEligibleReceivers();
        var allowed = salesBoundary.filterTransferTargets(ownerUserId, candidates.stream().map(LeadAssignmentUserRespVO::getId).toList());
        return candidates.stream().filter(user -> !ownerUserId.equals(user.getId()) && allowed.contains(user.getId())).toList();
    }

    @Transactional(rollbackFor = Exception.class)
    @ZsjosPermission(bizType = "lead", bizId = "#leadId", action = "owner-transfer")
    @cn.iocoder.yudao.module.zsjos.service.lead.flow.LeadFlowOperation(value="本人转派", target="lead", id=0, actor=2, actorType="ADMIN")
    public void transfer(Long leadId, Long targetUserId, Long ownerUserId, String reason, String idempotencyKey) {
        LeadDO lead = requireOwnedLead(leadId, ownerUserId);
        if (STATUS_WON.equals(lead.getStatus()) || STATUS_CLOSED.equals(lead.getStatus())) {
            throw exception(LEAD_PERMISSION_DENIED);
        }
        if (salesOrderMapper.selectActiveByLeadId(leadId, ACTIVE_ORDER_STATUSES) != null) {
            throw exception(LEAD_OWNER_TRANSFER_DEAL_ACTIVE);
        }
        dispatchService.transferOwned(leadId, ownerUserId, targetUserId, ownerUserId, reason, idempotencyKey);
    }

    @Transactional(rollbackFor = Exception.class)
    @ZsjosPermission(bizType = "lead", bizId = "#leadId", action = "owner-release-public-sea")
    @cn.iocoder.yudao.module.zsjos.service.lead.flow.LeadFlowOperation(value="释放至公海", target="lead", id=0, actor=1, actorType="ADMIN")
    public void releaseToPublicSea(Long leadId, Long ownerUserId, String reason, String idempotencyKey) {
        requireOwnedLead(leadId, ownerUserId);
        publicSeaPoolService.enterManually(leadId, null, ownerUserId, reason, idempotencyKey);
    }

    private LeadDO requireOwnedLead(Long leadId, Long ownerUserId) {
        LeadDO lead = leadMapper.selectByIdForUpdate(leadId, TenantContextHolder.getRequiredTenantId());
        if (lead == null) throw exception(LEAD_NOT_EXISTS);
        if (!ownerUserId.equals(lead.getOwnerUserId())) throw exception(LEAD_PERMISSION_DENIED);
        return lead;
    }
}
