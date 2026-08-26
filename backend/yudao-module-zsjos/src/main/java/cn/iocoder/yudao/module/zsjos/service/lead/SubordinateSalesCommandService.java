package cn.iocoder.yudao.module.zsjos.service.lead;

import cn.hutool.crypto.digest.DigestUtil;
import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.subordinate.SubordinateBatchResultVO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.LeadDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.SubordinateSalesCommandDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.SubordinateSalesAuditLogDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.lead.LeadMapper;
import cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.qualification.LeadDispositionReqVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.qualification.LeadTransferReqVO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.lead.SubordinateSalesAuditLogMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.lead.SubordinateSalesCommandMapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.zsjos.enums.LeadConstants.*;
import static cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants.*;

@Service
public class SubordinateSalesCommandService {
    @Resource private LeadMapper leadMapper;
    @Resource private SubordinateSalesAuditLogMapper auditLogMapper;
    @Resource private LeadDispatchService dispatchService;
    @Resource private LeadObjectPermissionService permissionService;
    @Resource private LeadAgingPoolService agingPoolService;
    @Resource private LeadQualificationService qualificationService;
    @Resource private SubordinateSalesCommandMapper commandMapper;

    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void transferOne(Long leadId, Long targetUserId, Long managerUserId, String reason) {
        transferOne(leadId, targetUserId, managerUserId, reason, commandKey());
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void transferOne(Long leadId, Long targetUserId, Long managerUserId, String reason, String idempotencyKey) {
        String fingerprint = fingerprint("transfer", List.of(leadId), targetUserId, null, reason);
        if (!beginCommand("transfer", managerUserId, idempotencyKey, fingerprint)) return;
        LeadDO lead = requireManagedLeadForUpdate(leadId, managerUserId);
        if (!Set.of(STATUS_SUBMITTED, STATUS_SUSPENDED, STATUS_VALID, STATUS_CONVERTED).contains(lead.getStatus())
                || !Set.of(ASSIGNMENT_OWNED, ASSIGNMENT_RECYCLE_PENDING).contains(lead.getAssignmentStatus())) {
            throw exception(SUBORDINATE_LEAD_STATE_INVALID);
        }
        Long beforeOwner = ASSIGNMENT_RECYCLE_PENDING.equals(lead.getAssignmentStatus())
                ? lead.getRecycleSourceOwnerUserId() : lead.getOwnerUserId();
        if (Objects.equals(beforeOwner, targetUserId)) {
            throw exception(SUBORDINATE_SALES_TARGET_INVALID);
        }
        if (STATUS_SUSPENDED.equals(lead.getStatus())
                || ASSIGNMENT_RECYCLE_PENDING.equals(lead.getAssignmentStatus())) {
            LeadTransferReqVO request = new LeadTransferReqVO();
            request.setSalesUserId(targetUserId); request.setReason(reason); request.setIdempotencyKey(idempotencyKey);
            qualificationService.transfer(leadId, managerUserId, request);
        } else {
            dispatchService.adminTransfer(leadId, targetUserId, managerUserId, reason, idempotencyKey);
        }
        addAudit("lead_transfer", managerUserId, targetUserId, leadId,
                String.valueOf(beforeOwner), String.valueOf(targetUserId), reason);
        completeCommand(managerUserId, idempotencyKey, "true");
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void restoreOne(Long leadId, Long managerUserId, String reason) {
        restoreOne(leadId, managerUserId, reason, commandKey());
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void restoreOne(Long leadId, Long managerUserId, String reason, String idempotencyKey) {
        String fingerprint = fingerprint("restore", List.of(leadId), null, null, reason);
        if (!beginCommand("restore", managerUserId, idempotencyKey, fingerprint)) return;
        LeadDO lead = requireManagedLeadForUpdate(leadId, managerUserId);
        if (!STATUS_SUSPENDED.equals(lead.getStatus()) || !ASSIGNMENT_OWNED.equals(lead.getAssignmentStatus())) {
            throw exception(SUBORDINATE_LEAD_STATE_INVALID);
        }
        LeadDispositionReqVO request = disposition(reason, idempotencyKey);
        qualificationService.restore(leadId, managerUserId, request);
        addAudit("lead_restore", managerUserId, lead.getOwnerUserId(), leadId,
                STATUS_SUSPENDED, STATUS_SUBMITTED, reason);
        completeCommand(managerUserId, idempotencyKey, "true");
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void recycleOne(Long leadId, Long managerUserId, String reason) {
        recycleOne(leadId, managerUserId, reason, commandKey());
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void recycleOne(Long leadId, Long managerUserId, String reason, String idempotencyKey) {
        String fingerprint = fingerprint("recycle", List.of(leadId), null, null, reason);
        if (!beginCommand("recycle", managerUserId, idempotencyKey, fingerprint)) return;
        LeadDO lead = requireManagedLeadForUpdate(leadId, managerUserId);
        if (!ASSIGNMENT_OWNED.equals(lead.getAssignmentStatus())
                || !Set.of(STATUS_SUBMITTED, STATUS_SUSPENDED).contains(lead.getStatus())) {
            throw exception(SUBORDINATE_LEAD_STATE_INVALID);
        }
        LeadDispositionReqVO request = disposition(reason, idempotencyKey);
        if (STATUS_SUSPENDED.equals(lead.getStatus())) qualificationService.recycle(leadId, managerUserId, request);
        else qualificationService.supervisorRecycleOwned(leadId, managerUserId, request);
        addAudit("lead_recycle", managerUserId, lead.getOwnerUserId(), leadId,
                ASSIGNMENT_OWNED, ASSIGNMENT_RECYCLE_PENDING, reason);
        completeCommand(managerUserId, idempotencyKey, "true");
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void releaseClaimPoolOne(Long leadId, Long managerUserId, String reason) {
        releaseClaimPoolOne(leadId, managerUserId, reason, commandKey());
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void releaseClaimPoolOne(Long leadId, Long managerUserId, String reason, String idempotencyKey) {
        String fingerprint = fingerprint("release-claim-pool", List.of(leadId), null, null, reason);
        if (!beginCommand("release-claim-pool", managerUserId, idempotencyKey, fingerprint)) return;
        LeadDO lead = requireManagedLeadForUpdate(leadId, managerUserId);
        if (!STATUS_SUBMITTED.equals(lead.getStatus()) && !STATUS_SUSPENDED.equals(lead.getStatus())) {
            throw exception(SUBORDINATE_LEAD_STATE_INVALID);
        }
        if (!Set.of(ASSIGNMENT_OWNED, ASSIGNMENT_RECYCLE_PENDING).contains(lead.getAssignmentStatus())) {
            throw exception(SUBORDINATE_LEAD_STATE_INVALID);
        }
        LeadDispositionReqVO request = disposition(reason, idempotencyKey);
        if (STATUS_SUSPENDED.equals(lead.getStatus())
                || ASSIGNMENT_RECYCLE_PENDING.equals(lead.getAssignmentStatus())) {
            qualificationService.releaseToClaimPool(leadId, managerUserId, request);
        } else {
            qualificationService.supervisorReleaseOwnedToClaimPool(leadId, managerUserId, request);
        }
        addAudit("claim_pool_release", managerUserId, lead.getOwnerUserId(), leadId,
                lead.getAssignmentStatus(), ASSIGNMENT_PUBLIC_POOL, reason);
        completeCommand(managerUserId, idempotencyKey, "true");
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void releasePublicSeaOne(Long leadId, Long collaboratorUserId, Long managerUserId, String reason) {
        releasePublicSeaOne(leadId, collaboratorUserId, managerUserId, reason, commandKey());
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void releasePublicSeaOne(Long leadId, Long collaboratorUserId, Long managerUserId, String reason,
                                    String idempotencyKey) {
        String fingerprint = fingerprint("release-public-sea", List.of(leadId), null, collaboratorUserId, reason);
        if (!beginCommand("release-public-sea", managerUserId, idempotencyKey, fingerprint)) return;
        LeadDO lead = requireManagedLeadForUpdate(leadId, managerUserId);
        if (!ASSIGNMENT_OWNED.equals(lead.getAssignmentStatus())
                || !Set.of(STATUS_VALID, STATUS_CONVERTED).contains(lead.getStatus())
                || lead.getClosedAt() != null) {
            throw exception(SUBORDINATE_LEAD_STATE_INVALID);
        }
        agingPoolService.enterManually(leadId, collaboratorUserId, managerUserId, reason,
                "supervisor-public-sea:" + idempotencyKey);
        addAudit("public_sea_release", managerUserId, collaboratorUserId, leadId,
                null, "owner=" + lead.getOwnerUserId() + ",collaborator=" + collaboratorUserId, reason);
        completeCommand(managerUserId, idempotencyKey, "true");
    }

    public void validateManagedLeads(Collection<Long> leadIds, Long managerUserId) {
        Set<Long> managed = permissionService.getManagedUserIds(managerUserId);
        for (Long leadId : new java.util.LinkedHashSet<>(leadIds)) {
            LeadDO lead = leadMapper.selectById(leadId);
            if (lead == null) throw exception(LEAD_NOT_EXISTS);
            Long scopedOwner = lead.getOwnerUserId() != null ? lead.getOwnerUserId() : lead.getRecycleSourceOwnerUserId();
            if (scopedOwner == null || !managed.contains(scopedOwner)) throw exception(SUBORDINATE_LEAD_OWNER_CHANGED);
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public SubordinateBatchResultVO beginBatch(String action, Long managerUserId, String idempotencyKey,
                                                String fingerprint) {
        SubordinateSalesCommandDO existing = commandMapper.selectByOperatorAndKey(managerUserId, idempotencyKey);
        if (existing != null) return replay(existing, action, fingerprint, SubordinateBatchResultVO.class);
        SubordinateSalesCommandDO row = command(action, managerUserId, idempotencyKey, fingerprint);
        if (commandMapper.insertIgnore(TenantContextHolder.getRequiredTenantId(), row) == 1) return null;
        return replay(commandMapper.selectByOperatorAndKey(managerUserId, idempotencyKey),
                action, fingerprint, SubordinateBatchResultVO.class);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void completeBatch(Long managerUserId, String idempotencyKey, SubordinateBatchResultVO result) {
        completeCommand(managerUserId, idempotencyKey, JsonUtils.toJsonString(result));
    }

    public static String fingerprint(String action, Collection<Long> leadIds, Long targetUserId,
                                     Long collaboratorUserId, String reason) {
        java.util.List<Long> sortedIds = leadIds.stream().distinct().sorted().toList();
        return DigestUtil.sha256Hex(JsonUtils.toJsonString(Arrays.asList(
                action, sortedIds, targetUserId, collaboratorUserId, reason == null ? null : reason.trim())));
    }

    private boolean beginCommand(String action, Long managerUserId, String idempotencyKey, String fingerprint) {
        SubordinateSalesCommandDO existing = commandMapper.selectByOperatorAndKey(managerUserId, idempotencyKey);
        if (existing != null) {
            replay(existing, action, fingerprint, Boolean.class);
            return false;
        }
        SubordinateSalesCommandDO row = command(action, managerUserId, idempotencyKey, fingerprint);
        if (commandMapper.insertIgnore(TenantContextHolder.getRequiredTenantId(), row) == 1) return true;
        replay(commandMapper.selectByOperatorAndKey(managerUserId, idempotencyKey), action, fingerprint, Boolean.class);
        return false;
    }

    private SubordinateSalesCommandDO command(String action, Long managerUserId, String idempotencyKey,
                                               String fingerprint) {
        SubordinateSalesCommandDO row = new SubordinateSalesCommandDO();
        row.setActionType(action); row.setOperatorUserId(managerUserId); row.setIdempotencyKey(idempotencyKey);
        row.setRequestFingerprint(fingerprint); row.setCompleted(false); return row;
    }

    private <T> T replay(SubordinateSalesCommandDO row, String action, String fingerprint, Class<T> type) {
        if (row == null || !Objects.equals(row.getActionType(), action)
                || !Objects.equals(row.getRequestFingerprint(), fingerprint)
                || !Boolean.TRUE.equals(row.getCompleted()) || row.getResultJson() == null) {
            throw exception(SUBORDINATE_COMMAND_IDEMPOTENCY_CONFLICT);
        }
        return JsonUtils.parseObject(row.getResultJson(), type);
    }

    private void completeCommand(Long managerUserId, String idempotencyKey, String resultJson) {
        if (commandMapper.complete(TenantContextHolder.getRequiredTenantId(), managerUserId,
                idempotencyKey, resultJson) != 1) throw exception(SUBORDINATE_COMMAND_IDEMPOTENCY_CONFLICT);
    }

    private LeadDO requireManagedLeadForUpdate(Long leadId, Long managerUserId) {
        LeadDO lead = leadMapper.selectByIdForUpdate(leadId, TenantContextHolder.getRequiredTenantId());
        if (lead == null) throw exception(LEAD_NOT_EXISTS);
        Set<Long> managed = permissionService.getManagedUserIds(managerUserId);
        Long scopedOwner = lead.getOwnerUserId() != null ? lead.getOwnerUserId() : lead.getRecycleSourceOwnerUserId();
        if (scopedOwner == null || !managed.contains(scopedOwner)) {
            throw exception(SUBORDINATE_LEAD_OWNER_CHANGED);
        }
        return lead;
    }

    private static LeadDispositionReqVO disposition(String reason, String idempotencyKey) {
        LeadDispositionReqVO request = new LeadDispositionReqVO();
        request.setReason(reason); request.setIdempotencyKey(idempotencyKey); return request;
    }

    private static String commandKey() {
        return UUID.randomUUID().toString();
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
