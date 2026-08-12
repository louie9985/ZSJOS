package cn.iocoder.yudao.module.zsjos.service.lead;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.LeadDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.LeadPublicSeaRecordDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.SubordinateSalesAuditLogDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.lead.LeadMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.lead.LeadPublicSeaRecordMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.lead.SubordinateSalesAuditLogMapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.Set;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.zsjos.enums.LeadConstants.ASSIGNMENT_OWNED;
import static cn.iocoder.yudao.module.zsjos.enums.LeadConstants.STATUS_CLOSED;
import static cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants.*;

@Service
public class SubordinateSalesCommandService {
    @Resource private LeadMapper leadMapper;
    @Resource private LeadPublicSeaRecordMapper publicSeaRecordMapper;
    @Resource private SubordinateSalesAuditLogMapper auditLogMapper;
    @Resource private LeadDispatchService dispatchService;
    @Resource private LeadObjectPermissionService permissionService;

    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void transferOne(Long leadId, Long targetUserId, Long managerUserId, String reason) {
        LeadDO lead = requireManagedLeadForUpdate(leadId, managerUserId);
        if (!ASSIGNMENT_OWNED.equals(lead.getAssignmentStatus())) {
            throw exception(SUBORDINATE_LEAD_STATE_INVALID);
        }
        Long beforeOwner = lead.getOwnerUserId();
        if (Objects.equals(beforeOwner, targetUserId)) {
            throw exception(SUBORDINATE_SALES_TARGET_INVALID);
        }
        dispatchService.adminTransfer(leadId, targetUserId, managerUserId, reason);
        addAudit("lead_transfer", managerUserId, targetUserId, leadId,
                String.valueOf(beforeOwner), String.valueOf(targetUserId), reason);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void releasePublicSeaOne(Long leadId, Long collaboratorUserId, Long managerUserId, String reason) {
        LeadDO lead = requireManagedLeadForUpdate(leadId, managerUserId);
        if (!ASSIGNMENT_OWNED.equals(lead.getAssignmentStatus())
                || STATUS_CLOSED.equals(lead.getStatus()) || lead.getClosedAt() != null) {
            throw exception(SUBORDINATE_LEAD_STATE_INVALID);
        }
        if (publicSeaRecordMapper.selectByLeadId(leadId) != null) {
            throw exception(SUBORDINATE_LEAD_ALREADY_PUBLIC_SEA);
        }
        LeadPublicSeaRecordDO record = new LeadPublicSeaRecordDO();
        record.setLeadId(leadId);
        record.setOwnerUserId(lead.getOwnerUserId());
        record.setCollaboratorUserId(collaboratorUserId);
        record.setReleasedByUserId(managerUserId);
        record.setReleasedAt(LocalDateTime.now());
        record.setReleaseReason(reason);
        publicSeaRecordMapper.insert(record);
        addAudit("public_sea_release", managerUserId, collaboratorUserId, leadId,
                null, "owner=" + lead.getOwnerUserId() + ",collaborator=" + collaboratorUserId, reason);
    }

    private LeadDO requireManagedLeadForUpdate(Long leadId, Long managerUserId) {
        LeadDO lead = leadMapper.selectByIdForUpdate(leadId, TenantContextHolder.getRequiredTenantId());
        if (lead == null) throw exception(LEAD_NOT_EXISTS);
        Set<Long> managed = permissionService.getManagedUserIds(managerUserId);
        if (lead.getOwnerUserId() == null || !managed.contains(lead.getOwnerUserId())) {
            throw exception(SUBORDINATE_LEAD_OWNER_CHANGED);
        }
        return lead;
    }

    public void addAudit(String action, Long operator, Long target, Long leadId,
                         String before, String after, String reason) {
        SubordinateSalesAuditLogDO audit = new SubordinateSalesAuditLogDO();
        audit.setActionType(action);
        audit.setOperatorUserId(operator);
        audit.setTargetUserId(target);
        audit.setLeadId(leadId);
        audit.setBeforeValue(before);
        audit.setAfterValue(after);
        audit.setReason(reason);
        audit.setOccurredAt(LocalDateTime.now());
        auditLogMapper.insert(audit);
    }
}
