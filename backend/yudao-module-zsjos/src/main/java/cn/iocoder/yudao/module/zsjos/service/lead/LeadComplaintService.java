package cn.iocoder.yudao.module.zsjos.service.lead;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.infra.api.file.dto.FileInfoRespDTO;
import cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.complaint.LeadComplaintCreateReqVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.complaint.LeadComplaintDecisionReqVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.complaint.LeadComplaintPageReqVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.complaint.LeadComplaintRespVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.submission.LeadAttachmentReqVO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.LeadComplaintDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.LeadDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.lead.LeadComplaintMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.lead.LeadMapper;
import jakarta.annotation.Resource;
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
import static cn.iocoder.yudao.module.zsjos.enums.LeadNotifySceneConstants.COMPLAINT_FOUNDED;
import static cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants.*;

@Service
public class LeadComplaintService {

    @Resource private LeadComplaintMapper complaintMapper;
    @Resource private LeadMapper leadMapper;
    @Resource private LeadNotifyEventPublisher notifyPublisher;
    @Resource private LeadAttachmentService attachmentService;
    @Resource private LeadSubmissionIdentityService identityService;

    @Transactional(rollbackFor = Exception.class)
    public Long create(Long leadId, Long userId, LeadComplaintCreateReqVO req) {
        LeadComplaintDO replay = complaintMapper.selectByCreateKey(req.getIdempotencyKey());
        if (replay != null) return replay.getId();
        LeadDO lead = leadMapper.selectByIdForUpdate(leadId, TenantContextHolder.getRequiredTenantId());
        if (lead == null) throw exception(LEAD_NOT_EXISTS);
        if (!Objects.equals(lead.getSourceUserId(), userId)) throw exception(LEAD_PERMISSION_DENIED);
        identityService.requireHistoricalSubmitter(lead, userId);
        if (lead.getOwnerUserId() == null || Set.of(STATUS_INVALID, STATUS_CLOSED, STATUS_WON).contains(lead.getStatus())) {
            throw exception(LEAD_SUBMITTER_ACTION_STATE_INVALID);
        }
        Map<Long, FileInfoRespDTO> evidence = attachmentService.validateReferences(
                attachments(req.getEvidenceFileIds()), userId);
        LeadComplaintDO row = new LeadComplaintDO();
        row.setLeadId(leadId);
        row.setComplainantUserId(userId);
        row.setSalesUserId(lead.getOwnerUserId());
        row.setReason(req.getReason().trim());
        row.setEvidenceRefs(evidenceJson(evidence));
        row.setStatus("pending");
        row.setCreateIdempotencyKey(req.getIdempotencyKey());
        row.setVersion(0);
        complaintMapper.insert(row);
        return row.getId();
    }

    public PageResult<LeadComplaintRespVO> page(LeadComplaintPageReqVO req) {
        PageResult<LeadComplaintDO> page = complaintMapper.selectPage(req);
        Set<Long> leadIds = page.getList().stream().map(LeadComplaintDO::getLeadId).collect(Collectors.toSet());
        Map<Long, LeadDO> leads = leadIds.isEmpty() ? Map.of() : leadMapper.selectBatchIds(leadIds).stream()
                .collect(Collectors.toMap(LeadDO::getId, Function.identity()));
        return new PageResult<>(page.getList().stream().map(row -> toResp(row, leads.get(row.getLeadId()))).toList(),
                page.getTotal());
    }

    public PageResult<LeadComplaintRespVO> myPage(LeadComplaintPageReqVO req, Long userId) {
        PageResult<LeadComplaintDO> page = complaintMapper.selectMyPage(req, userId);
        Set<Long> leadIds = page.getList().stream().map(LeadComplaintDO::getLeadId).collect(Collectors.toSet());
        Map<Long, LeadDO> leads = leadIds.isEmpty() ? Map.of() : leadMapper.selectBatchIds(leadIds).stream()
                .collect(Collectors.toMap(LeadDO::getId, Function.identity()));
        return new PageResult<>(page.getList().stream().map(row -> toResp(row, leads.get(row.getLeadId()))).toList(),
                page.getTotal());
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
        if ("founded".equals(row.getResult())) {
            notifyPublisher.publish(COMPLAINT_FOUNDED, row.getLeadId(), "lead-complaint-founded:" + row.getId(),
                    handler, now, Map.of("ownerUserId", row.getSalesUserId(), "complaint.handlerUserId", handler));
        }
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

    private LeadComplaintRespVO toResp(LeadComplaintDO row, LeadDO lead) {
        LeadComplaintRespVO result = new LeadComplaintRespVO();
        result.setId(row.getId());
        result.setLeadId(row.getLeadId());
        result.setLeadNo(lead == null ? null : lead.getLeadNo());
        result.setComplainantUserId(row.getComplainantUserId());
        result.setSalesUserId(row.getSalesUserId());
        result.setReason(row.getReason());
        result.setEvidenceRefs(row.getEvidenceRefs());
        result.setStatus(row.getStatus());
        result.setResult(row.getResult());
        result.setHandlerUserId(row.getHandlerUserId());
        result.setHandlerOpinion(row.getHandlerOpinion());
        result.setHandlerEvidenceRefs(row.getHandlerEvidenceRefs());
        result.setHandledAt(row.getHandledAt());
        result.setCreateTime(row.getCreateTime());
        return result;
    }
}
