package cn.iocoder.yudao.module.zsjos.service.lead;

import cn.iocoder.yudao.framework.common.biz.system.dict.dto.DictDataRespDTO;
import cn.iocoder.yudao.framework.common.enums.CommonStatusEnum;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.system.api.dict.DictDataApi;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import cn.iocoder.yudao.module.system.api.user.dto.AdminUserRespDTO;
import cn.iocoder.yudao.module.infra.api.file.dto.FileInfoRespDTO;
import cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.assignment.LeadAssignmentUserRespVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.qualification.*;
import cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.submission.LeadAttachmentReqVO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.event.BusinessEventDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.LeadAssignmentHistoryDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.LeadDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.event.BusinessEventMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.lead.LeadAssignmentHistoryMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.lead.LeadMapper;
import cn.iocoder.yudao.module.zsjos.framework.permission.ZsjosPermission;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.zsjos.enums.LeadConstants.*;
import static cn.iocoder.yudao.module.zsjos.enums.LeadNotifySceneConstants.*;
import static cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants.*;

@Service
public class LeadQualificationServiceImpl implements LeadQualificationService {
    @Resource private LeadMapper leadMapper;
    @Resource private LeadAssignmentHistoryMapper historyMapper;
    @Resource private BusinessEventMapper eventMapper;
    @Resource private DictDataApi dictDataApi;
    @Resource private AdminUserApi adminUserApi;
    @Resource private LeadAssignmentService assignmentService;
    @Resource private LeadObjectPermissionService permissionService;
    @Resource private LeadLifecycleTaskService lifecycleTaskService;
    @Resource private LeadAttachmentService attachmentService;
    @Resource private LeadNotifyEventPublisher notifyEventPublisher;

    @Override
    @Transactional(rollbackFor = Exception.class)
    @ZsjosPermission(bizType = "lead", bizId = "#leadId", action = "qualify")
    public void judgeValid(Long leadId, Long userId, LeadQualificationCommandReqVO reqVO) {
        LeadDO lead = requireLeadForUpdate(leadId);
        String key = commandKey(reqVO.getIdempotencyKey());
        if (isIdempotent(key, leadId, userId, EVENT_LEAD_QUALIFIED_VALID)) return;
        requireQualificationPending(lead, userId);
        LocalDateTime now = LocalDateTime.now();
        lead.setStatus(STATUS_VALID);
        lead.setQualifiedByUserId(userId);
        lead.setQualifiedAt(now);
        lead.setInvalidReason(null);
        lead.setInvalidReasonLabelSnapshot(null);
        lead.setInvalidDescription(null);
        lead.setInvalidEvidenceRefs(null);
        leadMapper.updateById(lead);
        lifecycleTaskService.completeQualificationTask(leadId, lead.getQualificationRoundNo(), now);
        addEvent(EVENT_LEAD_QUALIFIED_VALID, lead, userId, STATUS_SUBMITTED, STATUS_VALID,
                null, key, Map.of("roundNo", lead.getQualificationRoundNo()));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @ZsjosPermission(bizType = "lead", bizId = "#leadId", action = "qualify")
    public void judgeInvalid(Long leadId, Long userId, LeadJudgeInvalidReqVO reqVO) {
        LeadDO lead = requireLeadForUpdate(leadId);
        String key = commandKey(reqVO.getIdempotencyKey());
        if (isIdempotent(key, leadId, userId, EVENT_LEAD_QUALIFIED_INVALID)) return;
        requireQualificationPending(lead, userId);
        DictDataRespDTO reason = dictDataApi.getDictDataList(DICT_INVALID_REASON).stream()
                .filter(item -> Objects.equals(item.getValue(), reqVO.getReasonCode()))
                .filter(item -> CommonStatusEnum.ENABLE.getStatus().equals(item.getStatus()))
                .findFirst().orElseThrow(() -> exception(LEAD_INVALID_REASON_INVALID));
        LocalDateTime now = LocalDateTime.now();
        lead.setStatus(STATUS_INVALID);
        lead.setQualifiedByUserId(userId);
        lead.setQualifiedAt(now);
        lead.setInvalidReason(reason.getValue());
        lead.setInvalidReasonLabelSnapshot(reason.getLabel());
        lead.setInvalidDescription(reqVO.getDescription().trim());
        lead.setInvalidEvidenceRefs(buildEvidenceJson(reqVO.getAttachments(), userId));
        leadMapper.updateById(lead);
        lifecycleTaskService.completeQualificationTask(leadId, lead.getQualificationRoundNo(), now);
        addEvent(EVENT_LEAD_QUALIFIED_INVALID, lead, userId, STATUS_SUBMITTED, STATUS_INVALID,
                reqVO.getDescription().trim(), key,
                Map.of("roundNo", lead.getQualificationRoundNo(), "reasonCode", reason.getValue(),
                        "reasonLabel", reason.getLabel()));
    }

    @Override
    public PageResult<LeadQualificationExceptionRespVO> getExceptionPage(
            LeadQualificationExceptionPageReqVO reqVO, Long userId) {
        if (!Set.of(STATUS_SUSPENDED, ASSIGNMENT_RECYCLE_PENDING).contains(reqVO.getType())) {
            throw exception(LEAD_QUALIFICATION_EXCEPTION_TYPE_INVALID);
        }
        boolean manageAll = permissionService.hasQualificationManageAll();
        Set<Long> managedUserIds = manageAll ? Set.of() : permissionService.getManagedUserIds(userId);
        if (!manageAll && managedUserIds.isEmpty()) return PageResult.empty();
        PageResult<LeadDO> page = leadMapper.selectQualificationExceptionPage(reqVO, reqVO.getType(),
                managedUserIds, manageAll);
        Set<Long> userIds = new HashSet<>();
        page.getList().forEach(lead -> {
            if (lead.getOwnerUserId() != null) userIds.add(lead.getOwnerUserId());
            if (lead.getRecycleSourceOwnerUserId() != null) userIds.add(lead.getRecycleSourceOwnerUserId());
        });
        Map<Long, AdminUserRespDTO> users = adminUserApi.getUserMap(userIds);
        return new PageResult<>(page.getList().stream().map(lead -> convert(lead, users)).toList(), page.getTotal());
    }

    @Override
    @ZsjosPermission(bizType = "lead", bizId = "#leadId", action = "qualification-manage")
    public List<LeadAssignmentUserRespVO> getTransferCandidates(Long leadId, Long userId) {
        LeadDO lead = leadMapper.selectById(leadId);
        if (lead == null) throw exception(LEAD_NOT_EXISTS);
        boolean suspended = STATUS_SUSPENDED.equals(lead.getStatus())
                && ASSIGNMENT_OWNED.equals(lead.getAssignmentStatus());
        boolean recycled = ASSIGNMENT_RECYCLE_PENDING.equals(lead.getAssignmentStatus());
        if (!suspended && !recycled) throw exception(LEAD_QUALIFICATION_DISPOSITION_INVALID);
        boolean manageAll = permissionService.hasQualificationManageAll();
        Set<Long> managedUserIds = manageAll ? Set.of() : permissionService.getManagedUserIds(userId);
        return assignmentService.getEligibleSalesUsers().stream()
                .filter(item -> manageAll || managedUserIds.contains(item.getId()))
                .filter(item -> !suspended || !Objects.equals(item.getId(), lead.getOwnerUserId()))
                .toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @ZsjosPermission(bizType = "lead", bizId = "#leadId", action = "qualification-manage")
    public void restore(Long leadId, Long userId, LeadDispositionReqVO reqVO) {
        LeadDO lead = requireLeadForUpdate(leadId);
        String key = dispositionKey(reqVO.getIdempotencyKey());
        if (isIdempotent(key, leadId, userId, EVENT_LEAD_RESTORED)) return;
        if (!STATUS_SUSPENDED.equals(lead.getStatus()) || !ASSIGNMENT_OWNED.equals(lead.getAssignmentStatus())) {
            throw exception(LEAD_QUALIFICATION_DISPOSITION_INVALID);
        }
        requireEligibleSales(lead.getOwnerUserId(), userId, false);
        LocalDateTime now = LocalDateTime.now();
        lifecycleTaskService.cancelQualificationTask(leadId, lead.getQualificationRoundNo(), now, "主管恢复并重启判定");
        lead.setStatus(STATUS_SUBMITTED);
        lifecycleTaskService.createQualificationTask(lead, lead.getOwnerUserId(), now);
        leadMapper.updateById(lead);
        addEvent(EVENT_LEAD_RESTORED, lead, userId, STATUS_SUSPENDED, STATUS_SUBMITTED,
                reqVO.getReason().trim(), key, Map.of("ownerUserId", lead.getOwnerUserId()));
        publishDispositionNotification(QUALIFICATION_RESTORED, lead, userId, key,
                lead.getOwnerUserId(), null, reqVO.getReason().trim(), now);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @ZsjosPermission(bizType = "lead", bizId = "#leadId", action = "qualification-manage")
    public void transfer(Long leadId, Long userId, LeadTransferReqVO reqVO) {
        LeadDO lead = requireLeadForUpdate(leadId);
        String key = dispositionKey(reqVO.getIdempotencyKey());
        if (isIdempotent(key, leadId, userId, EVENT_LEAD_TRANSFERRED)) return;
        boolean suspended = STATUS_SUSPENDED.equals(lead.getStatus()) && ASSIGNMENT_OWNED.equals(lead.getAssignmentStatus());
        boolean recycled = ASSIGNMENT_RECYCLE_PENDING.equals(lead.getAssignmentStatus());
        if (!suspended && !recycled) throw exception(LEAD_QUALIFICATION_DISPOSITION_INVALID);
        if (suspended && Objects.equals(lead.getOwnerUserId(), reqVO.getSalesUserId())) {
            throw exception(LEAD_QUALIFICATION_TRANSFER_TARGET_INVALID);
        }
        requireEligibleSales(reqVO.getSalesUserId(), userId, true);
        LocalDateTime now = LocalDateTime.now();
        Long fromOwner = suspended ? lead.getOwnerUserId() : lead.getRecycleSourceOwnerUserId();
        lifecycleTaskService.cancelQualificationTask(leadId, lead.getQualificationRoundNo(), now, "主管转派");
        lifecycleTaskService.cancelFirstFollowUpTasks(leadId, now, "主管转派后直接进入待判定");
        lifecycleTaskService.cancelFollowUpReminders(leadId, now, "客资转派");
        LeadAssignmentHistoryDO history = addHistory(leadId, ACTION_TRANSFER, fromOwner,
                reqVO.getSalesUserId(), userId, reqVO.getReason(), now);
        lead.setStatus(STATUS_SUBMITTED);
        lead.setAssignmentStatus(ASSIGNMENT_OWNED);
        lead.setOwnerUserId(reqVO.getSalesUserId());
        lead.setRecycleSourceOwnerUserId(null);
        lead.setCurrentAssignmentHistoryId(history.getId());
        lead.setCurrentAssignmentFirstFollowUpAt(null);
        lead.setCurrentAssignmentFirstFollowUpDeadlineAt(null);
        lead.setNextFollowUpAt(null);
        lifecycleTaskService.createQualificationTask(lead, reqVO.getSalesUserId(), now);
        leadMapper.updateById(lead);
        addEvent(EVENT_LEAD_TRANSFERRED, lead, userId, suspended ? STATUS_SUSPENDED : ASSIGNMENT_RECYCLE_PENDING,
                STATUS_SUBMITTED, reqVO.getReason().trim(), key,
                Map.of("fromOwnerUserId", fromOwner, "toOwnerUserId", reqVO.getSalesUserId(),
                        "assignmentHistoryId", history.getId()));
        publishDispositionNotification(QUALIFICATION_TRANSFERRED, lead, userId, key,
                fromOwner, reqVO.getSalesUserId(), reqVO.getReason().trim(), now);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @ZsjosPermission(bizType = "lead", bizId = "#leadId", action = "qualification-manage")
    public void recycle(Long leadId, Long userId, LeadDispositionReqVO reqVO) {
        LeadDO lead = requireLeadForUpdate(leadId);
        String key = dispositionKey(reqVO.getIdempotencyKey());
        if (isIdempotent(key, leadId, userId, EVENT_LEAD_RECYCLED)) return;
        if (!STATUS_SUSPENDED.equals(lead.getStatus()) || !ASSIGNMENT_OWNED.equals(lead.getAssignmentStatus())) {
            throw exception(LEAD_QUALIFICATION_DISPOSITION_INVALID);
        }
        LocalDateTime now = LocalDateTime.now();
        Long fromOwner = lead.getOwnerUserId();
        lifecycleTaskService.cancelQualificationTask(leadId, lead.getQualificationRoundNo(), now, "主管回收");
        lifecycleTaskService.cancelFollowUpReminders(leadId, now, "客资回收");
        LeadAssignmentHistoryDO history = addHistory(leadId, ACTION_RECYCLE, fromOwner, null,
                userId, reqVO.getReason(), now);
        lead.setStatus(STATUS_SUBMITTED);
        lead.setAssignmentStatus(ASSIGNMENT_RECYCLE_PENDING);
        lead.setRecycleSourceOwnerUserId(fromOwner);
        lead.setOwnerUserId(null);
        clearCurrentAssignment(lead);
        leadMapper.updateById(lead);
        addEvent(EVENT_LEAD_RECYCLED, lead, userId, STATUS_SUSPENDED, ASSIGNMENT_RECYCLE_PENDING,
                reqVO.getReason().trim(), key, Map.of("fromOwnerUserId", fromOwner,
                        "assignmentHistoryId", history.getId()));
        publishDispositionNotification(QUALIFICATION_RECYCLED, lead, userId, key,
                fromOwner, null, reqVO.getReason().trim(), now);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @ZsjosPermission(bizType = "lead", bizId = "#leadId", action = "qualification-manage")
    public void releaseToClaimPool(Long leadId, Long userId, LeadDispositionReqVO reqVO) {
        LeadDO lead = requireLeadForUpdate(leadId);
        String key = dispositionKey(reqVO.getIdempotencyKey());
        if (isIdempotent(key, leadId, userId, EVENT_LEAD_RELEASED)) return;
        boolean suspended = STATUS_SUSPENDED.equals(lead.getStatus()) && ASSIGNMENT_OWNED.equals(lead.getAssignmentStatus());
        boolean recycled = ASSIGNMENT_RECYCLE_PENDING.equals(lead.getAssignmentStatus());
        if (!suspended && !recycled) throw exception(LEAD_QUALIFICATION_DISPOSITION_INVALID);
        LocalDateTime now = LocalDateTime.now();
        Long fromOwner = suspended ? lead.getOwnerUserId() : lead.getRecycleSourceOwnerUserId();
        lifecycleTaskService.cancelQualificationTask(leadId, lead.getQualificationRoundNo(), now, "主管释放到抢单池");
        lifecycleTaskService.cancelFollowUpReminders(leadId, now, "客资释放到抢单池");
        LeadAssignmentHistoryDO history = addHistory(leadId, ACTION_RELEASE, fromOwner, null,
                userId, reqVO.getReason(), now);
        lead.setStatus(STATUS_SUBMITTED);
        lead.setAssignmentStatus(ASSIGNMENT_PUBLIC_POOL);
        lead.setOwnerUserId(null);
        lead.setRecycleSourceOwnerUserId(fromOwner);
        lead.setPublicPoolAt(now);
        clearCurrentAssignment(lead);
        leadMapper.updateById(lead);
        addEvent(EVENT_LEAD_RELEASED, lead, userId,
                suspended ? STATUS_SUSPENDED : ASSIGNMENT_RECYCLE_PENDING, ASSIGNMENT_PUBLIC_POOL,
                reqVO.getReason().trim(), key, Map.of("fromOwnerUserId", fromOwner,
                        "assignmentHistoryId", history.getId()));
        publishDispositionNotification(QUALIFICATION_RELEASED, lead, userId, key,
                fromOwner, null, reqVO.getReason().trim(), now);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int processExpired() {
        LocalDateTime now = LocalDateTime.now();
        int processed = 0;
        for (LeadDO candidate : leadMapper.selectExpiredQualifications(now)) {
            LeadDO lead = requireLeadForUpdate(candidate.getId());
            if (!STATUS_SUBMITTED.equals(lead.getStatus()) || !ASSIGNMENT_OWNED.equals(lead.getAssignmentStatus())
                    || lead.getQualificationDeadlineAt() == null || lead.getQualificationDeadlineAt().isAfter(now)) {
                continue;
            }
            lead.setStatus(STATUS_SUSPENDED);
            lead.setSuspendedAt(now);
            leadMapper.updateById(lead);
            lifecycleTaskService.cancelQualificationTask(lead.getId(), lead.getQualificationRoundNo(), now,
                    "有效性判定超时自动挂起");
            addEvent(EVENT_LEAD_SUSPENDED, lead, null, STATUS_SUBMITTED, STATUS_SUSPENDED,
                    "有效性判定超时", "lead-suspended:" + lead.getId() + ":" + lead.getQualificationRoundNo(),
                    Map.of("roundNo", lead.getQualificationRoundNo(),
                            "deadlineAt", lead.getQualificationDeadlineAt().toString()));
            notifyEventPublisher.publish(QUALIFICATION_SUSPENDED, lead.getId(),
                    "notify:" + EVENT_LEAD_SUSPENDED + ":" + lead.getId() + ":" + lead.getQualificationRoundNo(),
                    null, now, Map.of("ownerUserId", lead.getOwnerUserId()));
            processed++;
        }
        return processed;
    }

    private void requireQualificationPending(LeadDO lead, Long userId) {
        if (!STATUS_SUBMITTED.equals(lead.getStatus()) || !ASSIGNMENT_OWNED.equals(lead.getAssignmentStatus())
                || lead.getQualificationDeadlineAt() == null || !Objects.equals(userId, lead.getOwnerUserId())) {
            throw exception(LEAD_QUALIFICATION_STATE_INVALID);
        }
    }

    private LeadDO requireLeadForUpdate(Long leadId) {
        LeadDO lead = leadMapper.selectByIdForUpdate(leadId, TenantContextHolder.getRequiredTenantId());
        if (lead == null) throw exception(LEAD_NOT_EXISTS);
        return lead;
    }

    private void requireEligibleSales(Long salesUserId, Long operatorUserId, boolean enforceManagerScope) {
        boolean manageAll = permissionService.hasQualificationManageAll();
        Set<Long> managed = manageAll ? Set.of() : permissionService.getManagedUserIds(operatorUserId);
        boolean eligible = assignmentService.getEligibleSalesUsers().stream()
                .anyMatch(item -> Objects.equals(item.getId(), salesUserId)
                        && (!enforceManagerScope || manageAll || managed.contains(item.getId())));
        if (!eligible) {
            throw exception(enforceManagerScope ? LEAD_QUALIFICATION_TRANSFER_TARGET_INVALID
                    : LEAD_QUALIFICATION_RESTORE_OWNER_INVALID);
        }
    }

    private boolean isIdempotent(String key, Long leadId, Long operatorUserId, String eventType) {
        BusinessEventDO existing = eventMapper.selectByIdempotencyKey(key);
        if (existing == null) return false;
        if (Objects.equals(existing.getAggregateId(), leadId)
                && Objects.equals(existing.getOperatorUserId(), operatorUserId)
                && Objects.equals(existing.getEventType(), eventType)) return true;
        throw exception(LEAD_QUALIFICATION_IDEMPOTENCY_CONFLICT);
    }

    private BusinessEventDO addEvent(String eventType, LeadDO lead, Long operatorUserId,
                                     String fromStatus, String toStatus, String reason,
                                     String idempotencyKey, Map<String, ?> refs) {
        BusinessEventDO event = new BusinessEventDO();
        event.setEventType(eventType);
        event.setAggregateType(BIZ_TYPE_LEAD);
        event.setAggregateId(lead.getId());
        event.setOperatorUserId(operatorUserId);
        event.setFromStatus(fromStatus);
        event.setToStatus(toStatus);
        event.setReason(reason);
        event.setRelatedObjectRefs(JsonUtils.toJsonString(refs));
        event.setOccurredAt(LocalDateTime.now());
        event.setIdempotencyKey(idempotencyKey);
        eventMapper.insert(event);
        return event;
    }

    private LeadAssignmentHistoryDO addHistory(Long leadId, String action, Long fromOwner, Long toOwner,
                                                Long operatorUserId, String reason, LocalDateTime occurredAt) {
        LeadAssignmentHistoryDO history = new LeadAssignmentHistoryDO();
        history.setLeadId(leadId);
        history.setActionType(action);
        history.setFromOwnerUserId(fromOwner);
        history.setToOwnerUserId(toOwner);
        history.setOperatorUserId(operatorUserId);
        history.setReason(reason.trim());
        history.setOccurredAt(occurredAt);
        historyMapper.insert(history);
        return history;
    }

    private void clearCurrentAssignment(LeadDO lead) {
        lead.setCurrentAssignmentHistoryId(null);
        lead.setCurrentAssignmentFirstFollowUpAt(null);
        lead.setCurrentAssignmentFirstFollowUpDeadlineAt(null);
        lead.setNextFollowUpAt(null);
        lead.setQualificationStartedAt(null);
        lead.setQualificationDeadlineAt(null);
        lead.setQualificationRuleSnapshot(null);
        lead.setSuspendedAt(null);
    }

    private LeadQualificationExceptionRespVO convert(LeadDO lead, Map<Long, AdminUserRespDTO> users) {
        LeadQualificationExceptionRespVO result = new LeadQualificationExceptionRespVO();
        result.setId(lead.getId());
        result.setSubmittedName(lead.getSubmittedName());
        result.setSubmittedMobile(lead.getSubmittedMobile());
        result.setStatus(lead.getStatus());
        result.setAssignmentStatus(lead.getAssignmentStatus());
        result.setHandlingStage(LeadHandlingStage.resolve(lead));
        result.setOwnerUserId(lead.getOwnerUserId());
        result.setOwnerUserName(userName(users, lead.getOwnerUserId()));
        result.setRecycleSourceOwnerUserId(lead.getRecycleSourceOwnerUserId());
        result.setRecycleSourceOwnerUserName(userName(users, lead.getRecycleSourceOwnerUserId()));
        result.setQualificationDeadlineAt(lead.getQualificationDeadlineAt());
        result.setSuspendedAt(lead.getSuspendedAt());
        return result;
    }

    private String userName(Map<Long, AdminUserRespDTO> users, Long userId) {
        AdminUserRespDTO user = userId == null ? null : users.get(userId);
        return user == null ? null : user.getNickname();
    }

    private String buildEvidenceJson(List<LeadAttachmentReqVO> attachments, Long userId) {
        if (attachments == null || attachments.isEmpty()) return null;
        Map<Long, FileInfoRespDTO> files = attachmentService.validateReferences(attachments, userId);
        List<Map<String, Object>> refs = new ArrayList<>();
        for (int index = 0; index < attachments.size(); index++) {
            FileInfoRespDTO file = files.get(attachments.get(index).getInfraFileId());
            Map<String, Object> ref = new LinkedHashMap<>();
            ref.put("infraFileId", file.getId()); ref.put("fileUrl", file.getUrl());
            ref.put("originalName", file.getName()); ref.put("contentType", file.getType());
            ref.put("fileSize", file.getSize()); ref.put("sort", index); refs.add(ref);
        }
        return JsonUtils.toJsonString(refs);
    }

    private String commandKey(String key) {
        return "lead-qualification:" + key;
    }

    private String dispositionKey(String key) {
        return "lead-disposition:" + key;
    }

    private void publishDispositionNotification(String sceneCode, LeadDO lead, Long operatorUserId,
                                                String eventKey, Long previousOwnerUserId,
                                                Long newOwnerUserId, String reason, LocalDateTime occurredAt) {
        Map<String, Object> context = new LinkedHashMap<>();
        context.put("ownerUserId", lead.getOwnerUserId());
        context.put("previousOwnerUserId", previousOwnerUserId);
        context.put("newOwnerUserId", newOwnerUserId);
        context.put("qualification.reason", reason);
        notifyEventPublisher.publish(sceneCode, lead.getId(), "notify:" + eventKey,
                operatorUserId, occurredAt, context);
    }
}
