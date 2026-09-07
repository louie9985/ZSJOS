package cn.iocoder.yudao.module.zsjos.service.lead;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.infra.api.file.FileApi;
import cn.iocoder.yudao.module.infra.api.file.dto.FileInfoRespDTO;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import cn.iocoder.yudao.module.system.api.user.dto.AdminUserRespDTO;
import cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.complaint.LeadComplaintCreateReqVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.complaint.LeadComplaintDecisionReqVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.complaint.LeadComplaintPageReqVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.complaint.LeadComplaintRespVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.submission.LeadAttachmentReqVO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.LeadComplaintDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.LeadDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.lead.LeadComplaintMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.lead.LeadMapper;
import cn.iocoder.yudao.module.zsjos.framework.permission.ZsjosPermission;
import jakarta.annotation.Resource;
import lombok.Data;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.zsjos.enums.LeadConstants.STATUS_CLOSED;
import static cn.iocoder.yudao.module.zsjos.enums.LeadConstants.STATUS_INVALID;
import static cn.iocoder.yudao.module.zsjos.enums.LeadConstants.STATUS_WON;
import static cn.iocoder.yudao.module.zsjos.enums.LeadConstants.PROVIDER_OWNER_PARTNER;
import static cn.iocoder.yudao.module.zsjos.enums.LeadConstants.PROVIDER_OWNER_SYSTEM_USER;
import static cn.iocoder.yudao.module.zsjos.enums.LeadConstants.ATTACHMENT_URL_EXPIRATION_SECONDS;
import static cn.iocoder.yudao.module.zsjos.enums.LeadNotifySceneConstants.COMPLAINT_FOUNDED;
import static cn.iocoder.yudao.module.zsjos.enums.LeadNotifySceneConstants.COMPLAINT_UNFOUNDED;
import static cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants.*;

@Service
public class LeadComplaintService {

    @Resource private LeadComplaintMapper complaintMapper;
    @Resource private LeadMapper leadMapper;
    @Resource private LeadNotifyEventPublisher notifyPublisher;
    @Resource private LeadAttachmentService attachmentService;
    @Resource private LeadSubmissionIdentityService identityService;
    @Resource private AdminUserApi adminUserApi;
    @Resource private FileApi fileApi;

    @Transactional(rollbackFor = Exception.class)
    public Long create(Long leadId, Long userId, LeadComplaintCreateReqVO req) {
        return createInternal(leadId, userId, null, userId, req);
    }

    @Transactional(rollbackFor = Exception.class)
    public Long createForPartner(Long leadId, Long accountId, Long partnerId, LeadComplaintCreateReqVO req) {
        return createInternal(leadId, null, partnerId, accountId, req);
    }

    private Long createInternal(Long leadId, Long userId, Long partnerId, Long attachmentOwnerId,
                                LeadComplaintCreateReqVO req) {
        LeadComplaintDO replay = complaintMapper.selectByCreateKey(req.getIdempotencyKey());
        if (replay != null) return replay.getId();
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
        if (lead.getOwnerUserId() == null || Set.of(STATUS_INVALID, STATUS_CLOSED, STATUS_WON).contains(lead.getStatus())) {
            throw exception(LEAD_SUBMITTER_ACTION_STATE_INVALID);
        }
        Map<Long, FileInfoRespDTO> evidence = partnerId == null
                ? attachmentService.validateReferences(attachments(req.getEvidenceFileIds()), attachmentOwnerId)
                : attachmentService.validatePartnerReferences(attachments(req.getEvidenceFileIds()), attachmentOwnerId);
        LeadComplaintDO row = new LeadComplaintDO();
        row.setLeadId(leadId);
        row.setComplainantUserId(userId);
        row.setPartnerId(partnerId);
        row.setSalesUserId(lead.getOwnerUserId());
        row.setReason(req.getReason().trim());
        row.setEvidenceRefs(evidenceJson(evidence));
        row.setStatus("pending");
        row.setCreateIdempotencyKey(req.getIdempotencyKey());
        row.setVersion(0);
        LocalDateTime now = LocalDateTime.now();
        complaintMapper.insert(row);
        leadMapper.touchActivity(leadId, now);
        return row.getId();
    }

    public PageResult<LeadComplaintRespVO> page(LeadComplaintPageReqVO req) {
        PageResult<LeadComplaintDO> page = complaintMapper.selectPage(req);
        Set<Long> leadIds = page.getList().stream().map(LeadComplaintDO::getLeadId).collect(Collectors.toSet());
        Map<Long, LeadDO> leads = leadIds.isEmpty() ? Map.of() : leadMapper.selectBatchIds(leadIds).stream()
                .collect(Collectors.toMap(LeadDO::getId, Function.identity()));
        Map<Long, String> userNames = userNames(page.getList());
        return new PageResult<>(page.getList().stream()
                .map(row -> toResp(row, leads.get(row.getLeadId()), userNames)).toList(),
                page.getTotal());
    }

    public PageResult<LeadComplaintRespVO> myPage(LeadComplaintPageReqVO req, Long userId) {
        PageResult<LeadComplaintDO> page = complaintMapper.selectMyPage(req, userId);
        Set<Long> leadIds = page.getList().stream().map(LeadComplaintDO::getLeadId).collect(Collectors.toSet());
        Map<Long, LeadDO> leads = leadIds.isEmpty() ? Map.of() : leadMapper.selectBatchIds(leadIds).stream()
                .collect(Collectors.toMap(LeadDO::getId, Function.identity()));
        Map<Long, String> userNames = userNames(page.getList());
        return new PageResult<>(page.getList().stream()
                .map(row -> toResp(row, leads.get(row.getLeadId()), userNames)).toList(),
                page.getTotal());
    }

    public PageResult<LeadComplaintRespVO> partnerPage(LeadComplaintPageReqVO req, Long partnerId) {
        PageResult<LeadComplaintDO> page = complaintMapper.selectPartnerPage(req, partnerId);
        Set<Long> leadIds = page.getList().stream().map(LeadComplaintDO::getLeadId).collect(Collectors.toSet());
        Map<Long, LeadDO> leads = leadIds.isEmpty() ? Map.of() : leadMapper.selectBatchIds(leadIds).stream()
                .collect(Collectors.toMap(LeadDO::getId, Function.identity()));
        Map<Long, String> userNames = userNames(page.getList());
        return new PageResult<>(page.getList().stream()
                .map(row -> toResp(row, leads.get(row.getLeadId()), userNames)).toList(),
                page.getTotal());
    }

    @ZsjosPermission(bizType = "lead", bizId = "#leadId", action = "read")
    public List<LeadComplaintRespVO> getLeadComplaints(Long leadId, Long userId) {
        LeadDO lead = leadMapper.selectById(leadId);
        if (lead == null) throw exception(LEAD_NOT_EXISTS);
        List<LeadComplaintDO> rows = complaintMapper.selectListByLeadId(leadId);
        Map<Long, String> userNames = userNames(rows);
        return rows.stream().map(row -> toResp(row, lead, userNames)).toList();
    }

    @Transactional(rollbackFor = Exception.class)
    public void decide(Long id, Long handler, LeadComplaintDecisionReqVO req) {
        LeadComplaintDO replay = complaintMapper.selectByDecisionKey(req.getIdempotencyKey());
        if (replay != null) return;
        if (!Set.of("founded", "unfounded").contains(req.getResult())) {
            throw exception(LEAD_COMPLAINT_RESULT_INVALID);
        }
        Map<Long, FileInfoRespDTO> evidence = attachmentService.validateReferences(
                attachments(req.getEvidenceFileIds()), handler);
        LeadComplaintDO row = complaintMapper.selectByIdForUpdate(id, TenantContextHolder.getRequiredTenantId());
        if (row == null) throw exception(LEAD_COMPLAINT_NOT_EXISTS);
        if (!"pending".equals(row.getStatus())) throw exception(LEAD_COMPLAINT_ALREADY_HANDLED);
        LocalDateTime now = LocalDateTime.now();
        row.setStatus("handled");
        row.setResult(req.getResult());
        row.setHandlerUserId(handler);
        row.setHandlerOpinion(req.getOpinion().trim());
        row.setHandlerEvidenceRefs(evidenceJson(evidence));
        row.setHandledAt(now);
        row.setDecisionIdempotencyKey(req.getIdempotencyKey());
        complaintMapper.updateById(row);
        leadMapper.touchActivity(row.getLeadId(), now);
        String sceneCode = "founded".equals(row.getResult()) ? COMPLAINT_FOUNDED : COMPLAINT_UNFOUNDED;
        Map<String, Object> context = new java.util.LinkedHashMap<>();
        if (row.getComplainantUserId() != null) context.put("complaint.complainantUserId", row.getComplainantUserId());
        if (row.getPartnerId() != null) context.put("complaint.partnerId", row.getPartnerId());
        if (row.getSalesUserId() != null) context.put("ownerUserId", row.getSalesUserId());
        context.put("complaint.result", row.getResult());
        context.put("complaint.handlerUserId", handler);
        context.put("complaint.handlerOpinion", row.getHandlerOpinion());
        notifyPublisher.publish(sceneCode, row.getLeadId(), "lead-complaint-" + row.getResult() + ":" + row.getId(),
                handler, now, context);
    }

    private List<LeadAttachmentReqVO> attachments(List<Long> ids) {
        return ids.stream().map(id -> {
            LeadAttachmentReqVO ref = new LeadAttachmentReqVO();
            ref.setInfraFileId(id);
            return ref;
        }).toList();
    }

    private String evidenceJson(Map<Long, FileInfoRespDTO> files) {
        return JsonUtils.toJsonString(files.values().stream().map(file -> Map.of(
                "infraFileId", file.getId(), "name", file.getName(), "type", file.getType(), "size", file.getSize()
        )).toList());
    }

    private LeadComplaintRespVO toResp(LeadComplaintDO row, LeadDO lead, Map<Long, String> userNames) {
        LeadComplaintRespVO result = new LeadComplaintRespVO();
        result.setId(row.getId());
        result.setLeadId(row.getLeadId());
        result.setLeadNo(lead == null ? null : lead.getLeadNo());
        result.setComplainantUserId(row.getComplainantUserId());
        result.setComplainantUserName(nameOf(userNames, row.getComplainantUserId()));
        result.setSalesUserId(row.getSalesUserId());
        result.setSalesUserName(nameOf(userNames, row.getSalesUserId()));
        result.setReason(row.getReason());
        result.setEvidenceRefs(row.getEvidenceRefs());
        result.setEvidence(toEvidence(row.getEvidenceRefs()));
        result.setStatus(row.getStatus());
        result.setResult(row.getResult());
        result.setHandlerUserId(row.getHandlerUserId());
        result.setHandlerUserName(nameOf(userNames, row.getHandlerUserId()));
        result.setHandlerOpinion(row.getHandlerOpinion());
        result.setHandlerEvidenceRefs(row.getHandlerEvidenceRefs());
        result.setHandlerEvidence(toEvidence(row.getHandlerEvidenceRefs()));
        result.setHandledAt(row.getHandledAt());
        result.setCreateTime(row.getCreateTime());
        return result;
    }

    private Map<Long, String> userNames(List<LeadComplaintDO> rows) {
        Set<Long> ids = rows.stream().flatMap(row -> java.util.stream.Stream.of(
                        row.getComplainantUserId(), row.getSalesUserId(), row.getHandlerUserId()))
                .filter(Objects::nonNull).collect(Collectors.toSet());
        if (ids.isEmpty()) return Map.of();
        return adminUserApi.getUserList(ids).stream().filter(user -> user.getNickname() != null)
                .collect(Collectors.toMap(AdminUserRespDTO::getId, AdminUserRespDTO::getNickname,
                        (left, right) -> left));
    }

    private String nameOf(Map<Long, String> userNames, Long userId) {
        return userId == null ? null : userNames.get(userId);
    }

    private List<LeadComplaintRespVO.EvidenceVO> toEvidence(String json) {
        List<EvidenceRef> refs = json == null ? List.of() : JsonUtils.parseArray(json, EvidenceRef.class);
        List<Long> ids = refs.stream().map(EvidenceRef::getInfraFileId).filter(Objects::nonNull).toList();
        Map<Long, String> urls = ids.isEmpty() ? Map.of()
                : fileApi.presignGetUrls(ids, ATTACHMENT_URL_EXPIRATION_SECONDS);
        return refs.stream().map(ref -> {
            LeadComplaintRespVO.EvidenceVO result = new LeadComplaintRespVO.EvidenceVO();
            result.setInfraFileId(ref.getInfraFileId());
            result.setFileUrl(urls.get(ref.getInfraFileId()));
            result.setOriginalName(ref.getName());
            result.setContentType(ref.getType());
            result.setFileSize(ref.getSize());
            return result;
        }).toList();
    }

    @Data
    private static class EvidenceRef {
        private Long infraFileId;
        private String name;
        private String type;
        private Long size;
    }
}
