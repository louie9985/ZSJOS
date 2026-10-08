package cn.iocoder.yudao.module.zsjos.service.lead;

import cn.hutool.crypto.digest.DigestUtil;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.security.core.service.SecurityFrameworkService;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.management.LeadManagementRespVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.subordinate.LeadOverturnValidReqVO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.event.BusinessEventDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.LeadDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.event.BusinessEventMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.lead.*;
import cn.iocoder.yudao.module.zsjos.framework.permission.ZsjosPermission;
import cn.iocoder.yudao.module.zsjos.service.cashback.CashbackService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;
import jakarta.validation.Valid;
import java.time.LocalDateTime;
import java.util.*;
import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.zsjos.enums.LeadConstants.*;
import static cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants.*;
import static cn.iocoder.yudao.module.zsjos.service.lead.SupervisorLeadOverturnPolicy.*;

@Service
@Validated
public class SupervisorLeadOverturnService {
    @Resource private LeadMapper leadMapper;
    @Resource private LeadAppealMapper appealMapper;
    @Resource private OpportunityMapper opportunityMapper;
    @Resource private LeadIntendedProductMapper intendedProductMapper;
    @Resource private BusinessEventMapper eventMapper;
    @Resource private LeadObjectPermissionService permissionService;
    @Resource private SecurityFrameworkService securityFrameworkService;
    @Resource private LeadAttachmentService attachmentService;
    @Resource private CashbackService cashbackService;
    @Resource private LeadNotifyEventPublisher notifyEventPublisher;

    public LeadManagementRespVO.ActionVO action(LeadDO lead, Long userId) {
        if (!STATUS_INVALID.equals(lead.getStatus()) || !securityFrameworkService.hasPermission(PERMISSION)
                || lead.getOwnerUserId() == null || !permissionService.getManagedUserIds(userId).contains(lead.getOwnerUserId())) return null;
        if (rejection(lead, appealMapper.selectListByLeadId(lead.getId()), opportunityMapper.selectByLeadId(lead.getId())) != null) return null;
        var action = new LeadManagementRespVO.ActionVO(ACTION, true);
        action.setQualificationToken(currentToken(lead));
        return action;
    }

    @Transactional(rollbackFor = Exception.class, isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    @ZsjosPermission(bizType = "lead", bizId = "#leadId", action = OBJECT_ACTION)
    public void overturn(Long leadId, Long userId, @Valid LeadOverturnValidReqVO request) {
        if (!securityFrameworkService.hasPermission(PERMISSION)) throw exception(LEAD_PERMISSION_DENIED);
        LeadDO lead = leadMapper.selectByIdForUpdate(leadId, TenantContextHolder.getRequiredTenantId());
        if (lead == null) throw exception(LEAD_NOT_EXISTS);
        if (lead.getOwnerUserId() == null || !permissionService.getManagedUserIds(userId).contains(lead.getOwnerUserId())) {
            throw exception(LEAD_PERMISSION_DENIED);
        }
        String key = "supervisor-overturn:" + request.getIdempotencyKey();
        String fingerprint = DigestUtil.sha256Hex(JsonUtils.toJsonString(Arrays.asList(leadId, userId,
                request.getQualificationToken(), request.getReason().trim(), request.getAttachments())));
        BusinessEventDO previous = eventMapper.selectByIdempotencyKeyForUpdate(key);
        if (previous != null) {
            Map<?, ?> refs = JsonUtils.parseObject(previous.getRelatedObjectRefs(), Map.class);
            if (!EVENT.equals(previous.getEventType()) || !Objects.equals(leadId, previous.getAggregateId())
                    || !Objects.equals(userId, previous.getOperatorUserId()) || refs == null
                    || !fingerprint.equals(refs.get("requestFingerprint"))) throw exception(SUBORDINATE_COMMAND_IDEMPOTENCY_CONFLICT);
            return;
        }
        if (!Objects.equals(currentToken(lead), request.getQualificationToken())) throw exception(STALE);
        // Appeal submission takes this same Lead lock before reading or creating any appeal round.
        var denied = rejection(lead, appealMapper.selectListByLeadId(leadId), opportunityMapper.selectByLeadId(leadId));
        if (denied != null) throw exception(denied);
        var files = attachmentService.validateReferences(request.getAttachments(), userId);
        List<Map<String, Object>> evidence = new ArrayList<>();
        for (var item : request.getAttachments()) {
            var file = files.get(item.getInfraFileId());
            Map<String, Object> ref = new LinkedHashMap<>();
            ref.put("infraFileId", file.getId()); ref.put("fileUrl", file.getUrl());
            ref.put("originalName", file.getName()); ref.put("contentType", file.getType());
            ref.put("fileSize", file.getSize()); ref.put("sort", evidence.size()); evidence.add(ref);
        }
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("requestFingerprint", fingerprint); snapshot.put("ownerUserId", lead.getOwnerUserId());
        snapshot.put("qualificationToken", request.getQualificationToken());
        snapshot.put("invalidReason", lead.getInvalidReason()); snapshot.put("invalidReasonLabelSnapshot", lead.getInvalidReasonLabelSnapshot());
        snapshot.put("invalidDescription", lead.getInvalidDescription()); snapshot.put("invalidEvidenceRefs", lead.getInvalidEvidenceRefs());
        snapshot.put("qualifiedByUserId", lead.getQualifiedByUserId()); snapshot.put("qualifiedAt", lead.getQualifiedAt());
        LocalDateTime now = LocalDateTime.now();
        LeadValidityRestoration.restore(lead, userId, request.getReason(), now, leadMapper, opportunityMapper, intendedProductMapper);
        BusinessEventDO event = new BusinessEventDO();
        event.setAggregateType("lead"); event.setAggregateId(leadId); event.setOperatorUserId(userId);
        event.setEventType(EVENT); event.setFromStatus(STATUS_INVALID); event.setToStatus(STATUS_VALID);
        event.setReason(request.getReason().trim()); event.setEvidenceRefs(JsonUtils.toJsonString(evidence));
        event.setRelatedObjectRefs(JsonUtils.toJsonString(snapshot)); event.setOccurredAt(now); event.setIdempotencyKey(key);
        try {
            eventMapper.insert(event);
        } catch (org.springframework.dao.DuplicateKeyException conflict) {
            throw exception(SUBORDINATE_COMMAND_IDEMPOTENCY_CONFLICT);
        }
        cashbackService.ensureValidCashback(leadId);
        notifyEventPublisher.publish(SCENE, leadId, key, userId, now,
                Map.of("overturn.reason", request.getReason().trim(), "ownerUserId", lead.getOwnerUserId()));
    }

    private String currentToken(LeadDO lead) {
        Long invalidEventId = eventMapper.selectByLeadId(lead.getId()).stream()
                .filter(event -> EVENT_LEAD_QUALIFIED_INVALID.equals(event.getEventType()))
                .map(BusinessEventDO::getId).filter(Objects::nonNull).max(Long::compareTo).orElse(null);
        return token(lead, invalidEventId);
    }
}
