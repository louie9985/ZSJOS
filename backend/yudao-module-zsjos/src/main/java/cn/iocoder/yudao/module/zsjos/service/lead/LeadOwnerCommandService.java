package cn.iocoder.yudao.module.zsjos.service.lead;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.assignment.LeadAssignmentUserRespVO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.LeadDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.lead.LeadMapper;
import cn.iocoder.yudao.module.zsjos.framework.permission.ZsjosPermission;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.zsjos.enums.LeadConstants.*;
import static cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants.*;

@Service
public class LeadOwnerCommandService {
    @Resource private LeadMapper leadMapper;
    @Resource private LeadDispatchService dispatchService;
    @Resource private LeadAgingPoolService agingPoolService;
    @Resource private LeadAssignmentService assignmentService;

    public List<LeadAssignmentUserRespVO> getTransferCandidates() {
        return assignmentService.getEligibleSalesUsers();
    }

    @Transactional(rollbackFor = Exception.class)
    @ZsjosPermission(bizType = "lead", bizId = "#leadId", action = "owner-transfer")
    public void transfer(Long leadId, Long targetUserId, Long ownerUserId, String reason, String idempotencyKey) {
        requireOwnedLead(leadId, ownerUserId);
        dispatchService.transferOwned(leadId, ownerUserId, targetUserId, ownerUserId, reason, idempotencyKey);
    }

    @Transactional(rollbackFor = Exception.class)
    @ZsjosPermission(bizType = "lead", bizId = "#leadId", action = "owner-release-public-sea")
    public void releaseToPublicSea(Long leadId, Long ownerUserId, String reason, String idempotencyKey) {
        requireOwnedLead(leadId, ownerUserId);
        agingPoolService.enterManually(leadId, null, ownerUserId, reason, idempotencyKey);
    }

    private LeadDO requireOwnedLead(Long leadId, Long ownerUserId) {
        LeadDO lead = leadMapper.selectByIdForUpdate(leadId, TenantContextHolder.getRequiredTenantId());
        if (lead == null) throw exception(LEAD_NOT_EXISTS);
        if (!ownerUserId.equals(lead.getOwnerUserId())) throw exception(LEAD_PERMISSION_DENIED);
        return lead;
    }
}
