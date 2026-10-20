package cn.iocoder.yudao.module.zsjos.service.educationteam;

import cn.iocoder.yudao.module.zsjos.service.lead.*;
import cn.hutool.crypto.digest.DigestUtil;
import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.zsjos.controller.admin.educationteam.vo.EducationTeamBatchResultVO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.LeadDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.educationteam.EducationTeamCommandDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.educationteam.EducationTeamAuditLogDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.lead.LeadMapper;
import cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.qualification.LeadDispositionReqVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.qualification.LeadTransferReqVO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.educationteam.EducationTeamAuditLogMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.educationteam.EducationTeamCommandMapper;
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
import static cn.iocoder.yudao.module.zsjos.service.educationteam.EducationTeamLeadActionPolicy.Action.*;

@Service
public class EducationTeamCommandService {
    @Resource private LeadMapper leadMapper;
    @Resource private LeadTeamTransferPolicy teamTransferPolicy;

    @Resource private EducationTeamAuditLogMapper auditLogMapper;
    @Resource private LeadDispatchService dispatchService;
    @Resource private EducationTeamScope scope;
    @Resource private LeadPublicSeaPoolService publicSeaPoolService;
    @Resource private LeadQualificationService qualificationService;
    @Resource private EducationTeamCommandMapper commandMapper;

    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void transferOne(Long leadId, Long targetUserId, Long managerUserId, String reason) {
        transferOne(leadId, targetUserId, managerUserId, reason, commandKey());
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void transferOne(Long leadId, Long targetUserId, Long managerUserId, String reason, String idempotencyKey) {
        transferOne(leadId, targetUserId, managerUserId, reason, idempotencyKey, null);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void transferOne(Long leadId, Long targetUserId, Long managerUserId, String reason, String idempotencyKey, Integer expectedVersion) {
        String fingerprint = fingerprint("transfer", List.of(leadId), targetUserId, null, reason);
        if (!beginCommand("transfer", managerUserId, idempotencyKey, fingerprint)) return;
        LeadDO lead = requireManagedLeadForUpdate(leadId, managerUserId);
        if (expectedVersion != null && !Objects.equals(expectedVersion, lead.getVersion())) throw exception(LEAD_FEEDBACK_VERSION_CONFLICT);
        EducationTeamLeadActionPolicy.requireAllowed(TRANSFER, lead);
        Long beforeOwner = ASSIGNMENT_RECYCLE_PENDING.equals(lead.getAssignmentStatus())
                ? lead.getRecycleSourceOwnerUserId() : lead.getOwnerUserId();
        if (Objects.equals(beforeOwner, targetUserId)) {
            throw exception(EducationTeamErrors.TARGET_INVALID);
        }
        scope.requireManaged(targetUserId,managerUserId);
        teamTransferPolicy.requireTarget(lead,targetUserId,managerUserId);
        if (STATUS_SUSPENDED.equals(lead.getStatus())
                || ASSIGNMENT_RECYCLE_PENDING.equals(lead.getAssignmentStatus())) {
            LeadTransferReqVO request = new LeadTransferReqVO();
            request.setSalesUserId(targetUserId); request.setReason(reason); request.setIdempotencyKey(scopedKey(idempotencyKey));
            qualificationService.transfer(leadId, managerUserId, request);
        } else {
            dispatchService.transferEducation(leadId, targetUserId, managerUserId, reason, scopedKey(idempotencyKey));

        }
        if (lead.getVersion() != null && leadMapper.updateVersionAndTouchActivity(leadId, lead.getVersion(), LocalDateTime.now()) != 1)
            throw exception(LEAD_FEEDBACK_VERSION_CONFLICT);
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
        restoreOne(leadId, managerUserId, reason, idempotencyKey, null);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void restoreOne(Long leadId, Long managerUserId, String reason, String idempotencyKey, Integer expectedVersion) {
        String fingerprint = fingerprint("restore", List.of(leadId), null, null, reason);
        if (!beginCommand("restore", managerUserId, idempotencyKey, fingerprint)) return;
        LeadDO lead = requireManagedLeadForUpdate(leadId, managerUserId);
        if (expectedVersion != null && !Objects.equals(expectedVersion, lead.getVersion())) throw exception(LEAD_FEEDBACK_VERSION_CONFLICT);
        EducationTeamLeadActionPolicy.requireAllowed(RESTORE, lead);
        LeadDispositionReqVO request = disposition(reason, idempotencyKey);
        qualificationService.restore(leadId, managerUserId, request);
        if (lead.getVersion() != null && leadMapper.updateVersionAndTouchActivity(leadId, lead.getVersion(), LocalDateTime.now()) != 1)
            throw exception(LEAD_FEEDBACK_VERSION_CONFLICT);
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
        EducationTeamLeadActionPolicy.requireAllowed(RECYCLE, lead);
        LeadDispositionReqVO request = disposition(reason, idempotencyKey);
        if (STATUS_SUSPENDED.equals(lead.getStatus())) qualificationService.recycle(leadId, managerUserId, request);
        else qualificationService.supervisorRecycleOwned(leadId, managerUserId, request);
        addAudit("lead_recycle", managerUserId, lead.getOwnerUserId(), leadId,
                ASSIGNMENT_OWNED, ASSIGNMENT_RECYCLE_PENDING, reason);
        completeCommand(managerUserId, idempotencyKey, "true");
    }

    public void validateManagedLeads(Collection<Long> leadIds, Long managerUserId) {
        Set<Long> managed = scope.managedIds(managerUserId);
        var rows=leadMapper.selectBatchIds(new java.util.LinkedHashSet<>(leadIds)).stream()
                .collect(java.util.stream.Collectors.toMap(LeadDO::getId,java.util.function.Function.identity()));
        for (Long leadId : new java.util.LinkedHashSet<>(leadIds)) {
            LeadDO lead = rows.get(leadId);
            if (lead == null) throw exception(LEAD_NOT_EXISTS);
            scope.requireEducationLead(lead);
            Long scopedOwner = lead.getOwnerUserId() != null ? lead.getOwnerUserId() : lead.getRecycleSourceOwnerUserId();
            if (scopedOwner == null || !managed.contains(scopedOwner)) throw exception(SUBORDINATE_LEAD_OWNER_CHANGED);
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public EducationTeamBatchResultVO beginBatch(String action, Long managerUserId, String idempotencyKey,
                                                String fingerprint) {
        EducationTeamCommandDO existing = commandMapper.selectByOperatorAndKey(managerUserId, scopedKey(idempotencyKey));
        if (existing != null) return replay(existing, action, fingerprint, EducationTeamBatchResultVO.class);
        EducationTeamCommandDO row = command(action, managerUserId, idempotencyKey, fingerprint);
        if (commandMapper.insertIgnore(TenantContextHolder.getRequiredTenantId(), row) == 1) return null;
        return replay(commandMapper.selectByOperatorAndKey(managerUserId, scopedKey(idempotencyKey)),
                action, fingerprint, EducationTeamBatchResultVO.class);
    }
    public EducationTeamBatchResultVO replayBatch(String action, Long managerUserId, String idempotencyKey, String fingerprint) {
        EducationTeamCommandDO existing = commandMapper.selectByOperatorAndKey(managerUserId,scopedKey(idempotencyKey));
        return existing == null ? null : replay(existing,action,fingerprint,EducationTeamBatchResultVO.class);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void completeBatch(Long managerUserId, String idempotencyKey, EducationTeamBatchResultVO result) {
        completeCommand(managerUserId, idempotencyKey, JsonUtils.toJsonString(result));
    }

    public static String fingerprint(String action, Collection<Long> leadIds, Long targetUserId,
                                     Long collaboratorUserId, String reason) {
        java.util.List<Long> sortedIds = leadIds.stream().distinct().sorted().toList();
        return DigestUtil.sha256Hex(JsonUtils.toJsonString(Arrays.asList(
                action, sortedIds, targetUserId, collaboratorUserId, reason == null ? null : reason.trim())));
    }

    private boolean beginCommand(String action, Long managerUserId, String idempotencyKey, String fingerprint) {
        EducationTeamCommandDO existing = commandMapper.selectByOperatorAndKey(managerUserId, scopedKey(idempotencyKey));
        if (existing != null) {
            replay(existing, action, fingerprint, Boolean.class);
            return false;
        }
        EducationTeamCommandDO row = command(action, managerUserId, idempotencyKey, fingerprint);
        if (commandMapper.insertIgnore(TenantContextHolder.getRequiredTenantId(), row) == 1) return true;
        replay(commandMapper.selectByOperatorAndKey(managerUserId, scopedKey(idempotencyKey)), action, fingerprint, Boolean.class);
        return false;
    }

    private EducationTeamCommandDO command(String action, Long managerUserId, String idempotencyKey,
                                               String fingerprint) {
        EducationTeamCommandDO row = new EducationTeamCommandDO();
        row.setActionType(action); row.setOperatorUserId(managerUserId); row.setIdempotencyKey(scopedKey(idempotencyKey));
        row.setRequestFingerprint(fingerprint); row.setCompleted(false); return row;
    }

    private <T> T replay(EducationTeamCommandDO row, String action, String fingerprint, Class<T> type) {
        if (row == null || !Objects.equals(row.getActionType(), action)
                || !Objects.equals(row.getRequestFingerprint(), fingerprint)
                || !Boolean.TRUE.equals(row.getCompleted()) || row.getResultJson() == null) {
            throw exception(SUBORDINATE_COMMAND_IDEMPOTENCY_CONFLICT);
        }
        return JsonUtils.parseObject(row.getResultJson(), type);
    }

    private void completeCommand(Long managerUserId, String idempotencyKey, String resultJson) {
        if (commandMapper.complete(TenantContextHolder.getRequiredTenantId(), managerUserId,
                scopedKey(idempotencyKey), resultJson) != 1) throw exception(SUBORDINATE_COMMAND_IDEMPOTENCY_CONFLICT);
    }

    private LeadDO requireManagedLeadForUpdate(Long leadId, Long managerUserId) {
        LeadDO lead = leadMapper.selectByIdForUpdate(leadId, TenantContextHolder.getRequiredTenantId());
        if (lead == null) throw exception(LEAD_NOT_EXISTS);
        Set<Long> managed = scope.managedIds(managerUserId);
        Long scopedOwner = lead.getOwnerUserId() != null ? lead.getOwnerUserId() : lead.getRecycleSourceOwnerUserId();
        if (scopedOwner == null || !managed.contains(scopedOwner)) {
            throw exception(SUBORDINATE_LEAD_OWNER_CHANGED);
        }
        scope.requireEducationLead(lead);
        return lead;
    }

    private static LeadDispositionReqVO disposition(String reason, String idempotencyKey) {
        LeadDispositionReqVO request = new LeadDispositionReqVO();
        request.setReason(reason); request.setIdempotencyKey(scopedKey(idempotencyKey)); return request;
    }

    private static String scopedKey(String key) { return "edu:"+DigestUtil.sha256Hex(key).substring(0,36); }
    private static String commandKey() {
        return UUID.randomUUID().toString();
    }

    public void addAudit(String action, Long operator, Long target, Long leadId,
                         String before, String after, String reason) {
        EducationTeamAuditLogDO audit = new EducationTeamAuditLogDO();
        audit.setActionType("education_"+action);
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
