package cn.iocoder.yudao.module.zsjos.service.lead;

import cn.hutool.core.util.StrUtil;
import cn.hutool.crypto.digest.DigestUtil;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.management.LeadSubmitterSupplementReqVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.management.LeadSubmitterAssistRequestReqVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.management.LeadSubmitterAssistReplyReqVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.management.LeadSubmitterAssistHistoryRespVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.management.LeadUrgeReqVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.submission.LeadAttachmentReqVO;
import cn.iocoder.yudao.module.infra.api.file.dto.FileInfoRespDTO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.event.BusinessEventDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.*;
import cn.iocoder.yudao.module.zsjos.dal.mysql.event.BusinessEventMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.lead.*;
import cn.iocoder.yudao.module.zsjos.framework.permission.ZsjosPermission;
import cn.iocoder.yudao.module.zsjos.service.product.ZsjosProductSkuService;
import cn.iocoder.yudao.module.zsjos.service.personnel.PartnerOwnershipService;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.personnel.PartnerOwnershipDO;
import cn.iocoder.yudao.module.zsjos.service.task.BusinessTaskCommandService;
import cn.iocoder.yudao.module.zsjos.service.task.BusinessTaskCreateCommand;
import jakarta.annotation.Resource;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.*;
import java.util.*;
import cn.iocoder.yudao.framework.common.pojo.PageParam;
import cn.iocoder.yudao.framework.common.pojo.PageResult;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.zsjos.enums.LeadConstants.*;
import static cn.iocoder.yudao.module.zsjos.enums.LeadNotifySceneConstants.*;
import static cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants.*;

@Service
public class LeadSubmitterActionService {
    @Resource private LeadMapper leadMapper;
    @Resource private BusinessEventMapper eventMapper;
    @Resource private LeadUrgeMapper urgeMapper; @Resource private LeadNotifyEventPublisher notifyPublisher;
    @Resource private LeadSubmissionIdentityService identityService;
    @Resource private LeadSubmitterAssistRequestMapper assistRequestMapper;
    @Resource private LeadAttachmentService attachmentService;
    @Resource private cn.iocoder.yudao.module.infra.api.file.FileApi fileApi;
    @Resource private LeadObjectPermissionService objectPermissionService;
    @Resource private PartnerOwnershipService partnerOwnershipService;
    @Resource private BusinessTaskCommandService businessTaskCommandService;
    @Resource private cn.iocoder.yudao.module.system.api.user.AdminUserApi adminUserApi;
    @Resource private PartnerMapper partnerMapper;

    @Transactional(rollbackFor = Exception.class)
    public void supplement(Long leadId, Long userId, LeadSubmitterSupplementReqVO req) {
        supplementInternal(leadId, userId, null, req);
    }

    @Transactional(rollbackFor = Exception.class)
    public void supplementForPartner(Long leadId, Long partnerId, LeadSubmitterSupplementReqVO req) {
        supplementInternal(leadId, null, partnerId, req);
    }

    private void supplementInternal(Long leadId, Long userId, Long partnerId, LeadSubmitterSupplementReqVO req) {
        LeadDO lead = requireSubmitterLeadForUpdate(leadId, userId, partnerId);
        String digest = supplementDigest(req);
        String subjectType = partnerId == null ? PROVIDER_OWNER_SYSTEM_USER : PROVIDER_OWNER_PARTNER;
        Long subjectId = partnerId == null ? userId : partnerId;
        BusinessEventDO replay = eventMapper.selectByIdempotencyKeyForUpdate(req.getIdempotencyKey());
        if (replay != null) {
            Map<?, ?> payload = LeadRemarkHistoryService.payload(replay);
            if (!LeadSupplementSnapshot.EVENT.equals(replay.getEventType())
                    || !BIZ_TYPE_LEAD.equals(replay.getAggregateType()) || !leadId.equals(replay.getAggregateId())
                    || payload == null || !digest.equals(payload.get("requestDigest"))
                    || !subjectType.equals(payload.get("submitterType"))
                    || !(payload.get("submitterId") instanceof Number id) || id.longValue() != subjectId) {
                throw exception(LEAD_SUPPLEMENT_IDEMPOTENCY_CONFLICT);
            }
            return;
        }
        requireActionable(lead);
        String remark = StrUtil.trimToNull(req.getRemark());
        List<LeadAttachmentReqVO> attachments = req.getAttachments() == null ? List.of() : req.getAttachments();
        Map<Long, FileInfoRespDTO> files = attachments.isEmpty() ? Map.of() : partnerId == null
                ? attachmentService.validateReferences(attachments, userId)
                : attachmentService.validatePartnerReferences(attachments, partnerId);
        List<Map<String, Object>> attachmentSnapshots = files.values().stream().map(file -> {
            Map<String, Object> snapshot = new LinkedHashMap<>();
            snapshot.put("infraFileId", file.getId()); snapshot.put("name", file.getName());
            snapshot.put("type", file.getType()); snapshot.put("size", file.getSize()); snapshot.put("url", file.getUrl());
            return snapshot;
        }).toList();
        Map<String,Object> before = Map.of("remark", Objects.toString(lead.getRemark(), ""));
        LocalDateTime now = LocalDateTime.now();
        LeadMapper.advanceActivity(lead, now);
        BusinessEventDO event = new BusinessEventDO(); event.setEventType("lead_submitter_supplemented");
        event.setAggregateType(BIZ_TYPE_LEAD); event.setAggregateId(leadId); event.setOperatorUserId(userId);
        String subjectName;
        if (partnerId != null) {
            var partner = partnerMapper.selectById(partnerId);
            subjectName = partner == null ? null : partner.getName();
        } else {
            var user = adminUserApi.getUser(userId);
            subjectName = user == null ? null : user.getNickname();
        }
        event.setEvidenceRefs(JsonUtils.toJsonString(attachmentSnapshots));
        event.setRelatedObjectRefs(JsonUtils.toJsonString(new LeadSupplementSnapshot(before,
                LeadSupplementSnapshot.MODE, remark,
                subjectType, subjectId, subjectName, digest)));
        event.setOccurredAt(now);
        event.setIdempotencyKey(req.getIdempotencyKey());
        try { eventMapper.insert(event); }
        catch (DuplicateKeyException ex) { throw exception(LEAD_SUPPLEMENT_IDEMPOTENCY_CONFLICT); }
        leadMapper.updateById(lead);
        if (lead.getOwnerUserId() != null && notifyPublisher != null) {
            Map<String, Object> context = new LinkedHashMap<>();
            context.put("lead.no", lead.getLeadNo()); context.put("ownerUserId", lead.getOwnerUserId());
            context.put("supplement.remark", remark); context.put("supplement.attachmentCount", attachmentSnapshots.size());
            notifyPublisher.publish(SUBMITTER_SUPPLEMENTED, leadId, "lead-supplement:" + event.getId(), userId, now, context);
        }
    }

    static String supplementDigest(LeadSubmitterSupplementReqVO req) {
        List<Long> attachments = req.getAttachments() == null ? List.of() : req.getAttachments().stream()
                .map(LeadAttachmentReqVO::getInfraFileId).toList();
        return DigestUtil.sha256Hex(JsonUtils.toJsonString(Arrays.asList(StrUtil.trimToNull(req.getRemark()), attachments)));
    }

    @Transactional(rollbackFor = Exception.class)
    public void urge(Long leadId, Long userId, LeadUrgeReqVO req) {
        urgeInternal(leadId, userId, null, req);
    }

    @Transactional(rollbackFor = Exception.class)
    public void urgeForPartner(Long leadId, Long partnerId, LeadUrgeReqVO req) {
        urgeInternal(leadId, null, partnerId, req);
    }

    private void urgeInternal(Long leadId, Long userId, Long partnerId, LeadUrgeReqVO req) {
        LeadDO lead = requireSubmitterLeadForUpdate(leadId, userId, partnerId);
        requireActionable(lead);
        if (lead.getOwnerUserId() == null) throw exception(LEAD_SUBMITTER_ACTION_STATE_INVALID);
        LocalDateTime now = LocalDateTime.now(ZoneId.of("Asia/Shanghai"));
        LeadUrgeDO row = new LeadUrgeDO(); row.setLeadId(leadId); row.setSubmitterUserId(userId); row.setPartnerId(partnerId);
        row.setTargetSalesUserId(lead.getOwnerUserId()); row.setUrgeDate(now.toLocalDate()); row.setReason(req.getReason().trim()); row.setUrgedAt(now);
        try { urgeMapper.insert(row); } catch (DuplicateKeyException ex) { throw exception(LEAD_URGE_DAILY_LIMIT); }
        Map<String, Object> context = new HashMap<>();
        context.put("submitterUserId", userId); context.put("partnerId", partnerId);
        context.put("ownerUserId", lead.getOwnerUserId()); context.put("urge.reason", row.getReason());
        notifyPublisher.publish("zsjos.lead.submitter_urged", leadId, "lead-urge:" + row.getId(), userId, now, context);
    }

    @Transactional(rollbackFor = Exception.class)
    @ZsjosPermission(bizType = BIZ_TYPE_LEAD, bizId = "#leadId", action = "request-submitter-assist")
    public Long requestAssist(Long leadId, Long userId, LeadSubmitterAssistRequestReqVO req) {
        objectPermissionService.check(leadId, "request-submitter-assist");
        List<LeadAttachmentReqVO> attachments = req.getAttachments() == null ? List.of() : req.getAttachments();
        Map<Long, FileInfoRespDTO> files = attachmentService.validateReferences(attachments, userId);
        String problem = req.getProblem().trim();
        String expectedAssistance = req.getExpectedAssistance().trim();
        String remark = StrUtil.trimToNull(req.getRemark());
        List<Map<String, Object>> attachmentSnapshots = files.values().stream().map(file -> {
            Map<String, Object> snapshot = new LinkedHashMap<String, Object>();
            snapshot.put("infraFileId", file.getId());
            snapshot.put("name", file.getName());
            snapshot.put("type", file.getType());
            snapshot.put("size", file.getSize());
            return snapshot;
        }).toList();
        String fingerprint = DigestUtil.sha256Hex(JsonUtils.toJsonString(
                List.of(leadId, userId, problem, expectedAssistance, Objects.toString(remark, ""), attachmentSnapshots)));
        LeadSubmitterAssistRequestDO replay = assistRequestMapper.selectByIdempotencyKey(req.getIdempotencyKey());
        if (replay != null) {
            if (!Objects.equals(replay.getRequestFingerprint(), fingerprint)) {
                throw exception(LEAD_SUBMITTER_ASSIST_IDEMPOTENCY_CONFLICT);
            }
            return replay.getId();
        }

        LeadDO lead = leadMapper.selectByIdForUpdate(leadId, TenantContextHolder.getRequiredTenantId());
        if (lead == null) throw exception(LEAD_NOT_EXISTS);
        if (assistRequestMapper.selectPendingByLeadId(leadId) != null) throw exception(LEAD_SUBMITTER_ASSIST_PENDING_EXISTS);
        AssistRecipient recipient = resolveAssistRecipient(lead);
        LocalDateTime now = LocalDateTime.now(ZoneId.of("Asia/Shanghai"));
        LeadSubmitterAssistRequestDO row = new LeadSubmitterAssistRequestDO();
        row.setLeadId(leadId); row.setLeadNoSnapshot(lead.getLeadNo()); row.setRequesterUserId(userId);
        row.setProblem(problem); row.setExpectedAssistance(expectedAssistance); row.setRemark(remark);
        row.setAttachmentSnapshotsJson(JsonUtils.toJsonString(attachmentSnapshots));
        row.setSubmitterTypeSnapshot(recipient.submitterType()); row.setSubmitterIdSnapshot(recipient.submitterId());
        row.setSubmitterNameSnapshot(recipient.submitterName()); row.setAssigneeUserIdSnapshot(recipient.assigneeUserId());
        row.setAssigneeNameSnapshot(recipient.assigneeName()); row.setRequestedAt(now);
        row.setRequestFingerprint(fingerprint); row.setIdempotencyKey(req.getIdempotencyKey());
        row.setStatus("pending"); row.setVersion(0);
        try {
            assistRequestMapper.insert(row);
        } catch (DuplicateKeyException duplicate) {
            replay = assistRequestMapper.selectByIdempotencyKey(req.getIdempotencyKey());
            if (replay == null || !Objects.equals(replay.getRequestFingerprint(), fingerprint)) {
                throw exception(LEAD_SUBMITTER_ASSIST_IDEMPOTENCY_CONFLICT);
            }
            return replay.getId();
        }

        Map<String, Object> context = new LinkedHashMap<>();
        context.put("assist.requestId", row.getId()); context.put("assist.problem", problem);
        context.put("assist.expectedAssistance", expectedAssistance);
        context.put("assist.remark", Objects.toString(remark, ""));
        context.put("assist.attachmentNames", files.values().stream().map(FileInfoRespDTO::getName).toList());
        context.put("partnerOwnerUserId", recipient.assigneeUserId());
        BusinessEventDO event = new BusinessEventDO();
        event.setEventType("lead_submitter_assist_requested"); event.setAggregateType(BIZ_TYPE_LEAD);
        event.setAggregateId(leadId); event.setOperatorUserId(userId); event.setReason(problem);
        event.setEvidenceRefs(JsonUtils.toJsonString(attachmentSnapshots));
        event.setRelatedObjectRefs(JsonUtils.toJsonString(context)); event.setOccurredAt(now);
        event.setIdempotencyKey("assist-event:" + row.getId()); eventMapper.insert(event);

        if (recipient.assigneeUserId() != null) {
            String taskLeadNo = StrUtil.isBlank(lead.getLeadNo()) ? "客资记录不可用" : lead.getLeadNo();
            businessTaskCommandService.create(new BusinessTaskCreateCommand(TASK_TYPE_SUBMITTER_ASSIST,
                    BIZ_TYPE_LEAD, leadId, recipient.assigneeUserId(), "提交人协助：" + taskLeadNo,
                    problem, "OPEN_LEAD_SUBMITTER_ASSIST", null, null, JsonUtils.toJsonString(context),
                    "lead-submitter-assist:" + row.getId()));
        }
        notifyPublisher.publish(SUBMITTER_ASSIST_REQUESTED, leadId, "lead-submitter-assist-message:" + row.getId(),
                userId, now, context);
        if (PROVIDER_OWNER_PARTNER.equals(recipient.submitterType()) && recipient.assigneeUserId() != null) {
            notifyPublisher.publish(PARTNER_ASSIST_REMINDER, leadId, "lead-partner-assist-reminder:" + row.getId(),
                    userId, now, context);
        }
        return row.getId();
    }

    @Transactional(rollbackFor = Exception.class)
    public void replyAssist(Long leadId, Long requestId, Long userId, LeadSubmitterAssistReplyReqVO req) {
        LeadSubmitterAssistRequestDO row = assistRequestMapper.selectById(requestId);
        if (row == null || !Objects.equals(row.getLeadId(), leadId) || !"pending".equals(row.getStatus()))
            throw exception(LEAD_SUBMITTER_ASSIST_NOT_EXISTS);
        if (!Objects.equals(row.getAssigneeUserIdSnapshot(), userId)) throw exception(LEAD_SUBMITTER_ASSIST_REPLY_FORBIDDEN);
        LeadDO lead = leadMapper.selectById(leadId);
        if (lead == null) throw exception(LEAD_NOT_EXISTS);
        objectPermissionService.check(leadId, "read");
        if (!Objects.equals(row.getVersion(), req.getVersion())) throw exception(LEAD_SUBMITTER_ASSIST_NOT_EXISTS);
        List<LeadAttachmentReqVO> attachments = req.getAttachments() == null ? List.of() : req.getAttachments();
        Map<Long, FileInfoRespDTO> files = attachmentService.validateReferences(attachments, userId);
        List<Map<String,Object>> snapshots = files.values().stream().map(file -> {
            Map<String,Object> item = new LinkedHashMap<>(); item.put("infraFileId", file.getId());
            item.put("name", file.getName()); item.put("type", file.getType()); item.put("size", file.getSize()); return item;
        }).toList();
        var user = adminUserApi.getUser(userId);
        row.setResponseRemark(StrUtil.trim(req.getRemark())); row.setResponseAttachmentSnapshotsJson(JsonUtils.toJsonString(snapshots));
        row.setResponderUserIdSnapshot(userId); row.setResponderNameSnapshot(user == null ? null : user.getNickname());
        row.setRespondedAt(LocalDateTime.now()); row.setStatus("completed"); row.setVersion(row.getVersion() + 1);
        assistRequestMapper.updateById(row);
        businessTaskCommandService.completeByKey("lead-submitter-assist:" + requestId, row.getRespondedAt());
        Map<String,Object> context = new LinkedHashMap<>(); context.put("assist.requestId", requestId);
        context.put("assist.response", row.getResponseRemark()); context.put("assist.leadNo", row.getLeadNoSnapshot());
        context.put("assist.requesterUserId", row.getRequesterUserId());
        notifyPublisher.publish(SUBMITTER_ASSIST_REPLIED, leadId, "lead-submitter-assist-replied:" + requestId,
                userId, row.getRespondedAt(), context);
    }

    public PageResult<LeadSubmitterAssistHistoryRespVO> history(Long leadId, Long userId, PageParam page) {
        LeadDO lead = leadMapper.selectById(leadId);
        if (lead == null) throw exception(LEAD_NOT_EXISTS);
        objectPermissionService.check(leadId, "read");
        PageResult<LeadSubmitterAssistRequestDO> rows = assistRequestMapper.selectPageByLeadId(leadId, page);
        List<LeadSubmitterAssistHistoryRespVO> result = rows.getList().stream().map(row -> {
            LeadSubmitterAssistHistoryRespVO vo = new LeadSubmitterAssistHistoryRespVO();
            vo.setId(row.getId()); vo.setLeadNo(row.getLeadNoSnapshot()); vo.setStatus(row.getStatus()); vo.setVersion(row.getVersion());
            vo.setProblem(row.getProblem()); vo.setExpectedAssistance(row.getExpectedAssistance());
            vo.setRemark(row.getRemark());
            // Legacy requests store the requester ID, but no name snapshot.
            var requester = row.getRequesterUserId() == null ? null : adminUserApi.getUser(row.getRequesterUserId());
            vo.setRequesterName(requester == null ? null : requester.getNickname());
            vo.setRequestAttachments(historyAttachments(row.getAttachmentSnapshotsJson()));
            vo.setResponseAttachments(historyAttachments(row.getResponseAttachmentSnapshotsJson()));
            vo.setSubmitterName(row.getSubmitterNameSnapshot()); vo.setAssigneeName(row.getAssigneeNameSnapshot());
            vo.setRequestedAt(row.getRequestedAt()); vo.setResponseRemark(row.getResponseRemark());
            vo.setResponderName(row.getResponderNameSnapshot()); vo.setRespondedAt(row.getRespondedAt());
            return vo;
        }).toList();
        return new PageResult<>(result, rows.getTotal());
    }

    private List<LeadSubmitterAssistHistoryRespVO.Attachment> historyAttachments(String snapshot) {
        if (StrUtil.isBlank(snapshot)) return List.of();
        var attachments = JsonUtils.parseArray(snapshot, LeadSubmitterAssistHistoryRespVO.Attachment.class);
        if (attachments == null) return List.of();
        // Only sign stored references after the parent Lead read check; never persist signed URLs.
        for (var attachment : attachments) {
            attachment.setUrl(attachment.getInfraFileId() == null ? null
                    : fileApi.presignGetUrl(attachment.getInfraFileId(), 600));
        }
        return attachments;
    }

    private AssistRecipient resolveAssistRecipient(LeadDO lead) {
        if (PROVIDER_OWNER_SYSTEM_USER.equals(lead.getProviderOwnerType()) && lead.getProviderOwnerId() != null) {
            return new AssistRecipient(PROVIDER_OWNER_SYSTEM_USER, lead.getProviderOwnerId(),
                    lead.getProviderOwnerNameSnapshot(), lead.getProviderOwnerId(), lead.getProviderOwnerNameSnapshot());
        }
        if (PROVIDER_OWNER_PARTNER.equals(lead.getProviderOwnerType()) && lead.getProviderOwnerId() != null) {
            PartnerOwnershipDO ownership = partnerOwnershipService.getByPartnerId(lead.getProviderOwnerId());
            Long assigneeId = ownership == null ? null : ownership.getEmployeeUserId();
            String assigneeName = ownership == null ? null : ownership.getEmployeeNameSnapshot();
            return new AssistRecipient(PROVIDER_OWNER_PARTNER, lead.getProviderOwnerId(),
                    lead.getProviderOwnerNameSnapshot(), assigneeId, assigneeName);
        }
        throw exception(LEAD_SUBMITTER_ASSIST_RECIPIENT_MISSING);
    }

    private LeadDO requireSubmitterLeadForUpdate(Long leadId, Long userId, Long partnerId) {
        LeadDO lead = leadMapper.selectByIdForUpdate(leadId, TenantContextHolder.getRequiredTenantId());
        if (lead == null) throw exception(LEAD_NOT_EXISTS);
        if (partnerId != null) {
            if (!PROVIDER_OWNER_PARTNER.equals(lead.getProviderOwnerType())
                    || !Objects.equals(lead.getProviderOwnerId(), partnerId)) throw exception(LEAD_PERMISSION_DENIED);
        } else {
            if (!PROVIDER_OWNER_SYSTEM_USER.equals(lead.getProviderOwnerType())
                    || !Objects.equals(lead.getProviderOwnerId(), userId)) throw exception(LEAD_PERMISSION_DENIED);
            identityService.requireHistoricalSubmitter(lead, userId);
        }
        return lead;
    }

    private void requireActionable(LeadDO lead) {
        if (Set.of(STATUS_INVALID, STATUS_CLOSED, STATUS_WON).contains(lead.getStatus())) {
            throw exception(LEAD_SUBMITTER_ACTION_STATE_INVALID);
        }
    }
    private record AssistRecipient(String submitterType, Long submitterId, String submitterName,
                                   Long assigneeUserId, String assigneeName) {}
}
