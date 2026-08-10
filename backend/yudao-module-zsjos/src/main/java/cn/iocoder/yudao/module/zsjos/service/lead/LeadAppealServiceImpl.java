package cn.iocoder.yudao.module.zsjos.service.lead;

import cn.iocoder.yudao.framework.common.biz.system.dict.dto.DictDataRespDTO;
import cn.iocoder.yudao.framework.common.enums.CommonStatusEnum;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.bpm.api.task.BpmProcessInstanceApi;
import cn.iocoder.yudao.module.bpm.api.task.BpmProcessTaskApi;
import cn.iocoder.yudao.module.bpm.api.task.dto.*;
import cn.iocoder.yudao.module.infra.api.file.FileApi;
import cn.iocoder.yudao.module.infra.api.file.dto.FileInfoRespDTO;
import cn.iocoder.yudao.module.system.api.dept.DeptApi;
import cn.iocoder.yudao.module.system.api.dept.dto.DeptRespDTO;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import cn.iocoder.yudao.module.system.api.permission.RoleApi;
import cn.iocoder.yudao.module.system.api.permission.dto.RoleRespDTO;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import cn.iocoder.yudao.module.system.api.user.dto.AdminUserRespDTO;
import cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.appeal.*;
import cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.submission.LeadAttachmentReqVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.submission.LeadAttachmentUploadRespVO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.LeadAppealDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.LeadDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.event.BusinessEventDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.event.BusinessEventMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.lead.LeadAppealMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.lead.LeadMapper;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import jakarta.annotation.Resource;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.*;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.zsjos.enums.LeadConstants.*;
import static cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants.*;

@Service
public class LeadAppealServiceImpl implements LeadAppealService {

    @Resource private LeadAppealMapper appealMapper;
    @Resource private LeadMapper leadMapper;
    @Resource private BusinessEventMapper eventMapper;
    @Resource private LeadNotifyEventPublisher notifyEventPublisher;
    @Resource private LeadAttachmentService attachmentService;
    @Resource private FileApi fileApi;
    @Resource private AdminUserApi adminUserApi;
    @Resource private DeptApi deptApi;
    @Resource private RoleApi roleApi;
    @Resource private PermissionApi permissionApi;
    @Resource private BpmProcessInstanceApi processInstanceApi;
    @Resource private BpmProcessTaskApi processTaskApi;

    @Override
    public List<LeadAppealRespVO> getLeadAppeals(Long leadId, Long userId) {
        LeadDO lead = requireLead(leadId);
        if (!Objects.equals(lead.getSourceUserId(), userId) && !Objects.equals(lead.getOwnerUserId(), userId)) {
            throw exception(LEAD_APPEAL_PERMISSION_DENIED);
        }
        return appealMapper.selectListByLeadId(leadId).stream().map(item -> convert(item, lead, null)).toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long submit(Long leadId, Long userId, LeadAppealSubmitReqVO reqVO) {
        LeadDO lead = requireLeadForUpdate(leadId);
        LeadAppealDO duplicate = appealMapper.selectBySubmissionIdempotencyKey(reqVO.getIdempotencyKey());
        if (duplicate != null) {
            if (Objects.equals(duplicate.getLeadId(), leadId) && Objects.equals(duplicate.getApplicantUserId(), userId)) {
                return duplicate.getId();
            }
            throw exception(LEAD_APPEAL_IDEMPOTENCY_CONFLICT);
        }
        if (!STATUS_INVALID.equals(lead.getStatus()) || !Objects.equals(lead.getSourceUserId(), userId)) {
            throw exception(LEAD_APPEAL_STATE_INVALID);
        }
        LeadAppealDO previous = appealMapper.selectLatestByLeadId(leadId);
        int roundNo = previous == null ? 1 : previous.getRoundNo() + 1;
        if (roundNo > 3 || previous != null && !APPEAL_STATUS_UPHELD.equals(previous.getStatus())) {
            throw exception(LEAD_APPEAL_STATE_INVALID);
        }
        List<Long> reviewers = resolveReviewers(roundNo, lead);
        String evidence = buildEvidenceJson(reqVO.getAttachments(), userId);
        LocalDateTime now = LocalDateTime.now();
        LeadAppealDO appeal = new LeadAppealDO();
        appeal.setLeadId(leadId);
        appeal.setRoundNo(roundNo);
        appeal.setReviewStage(stage(roundNo));
        appeal.setStatus(reviewingStatus(roundNo));
        appeal.setApplicantUserId(userId);
        appeal.setReason(reqVO.getReason().trim());
        appeal.setEvidenceRefs(evidence);
        appeal.setInvalidReasonSnapshot(lead.getInvalidReasonLabelSnapshot() != null
                ? lead.getInvalidReasonLabelSnapshot() : lead.getInvalidReason());
        appeal.setInvalidDescriptionSnapshot(lead.getInvalidDescription());
        appeal.setInvalidEvidenceRefsSnapshot(lead.getInvalidEvidenceRefs());
        appeal.setSubmittedAt(now);
        appeal.setSubmissionIdempotencyKey(reqVO.getIdempotencyKey());
        appealMapper.insert(appeal);
        try {
            BpmProcessInstanceCreateReqDTO processReq = new BpmProcessInstanceCreateReqDTO();
            processReq.setProcessDefinitionKey(APPEAL_PROCESS_DEFINITION_KEY);
            processReq.setBusinessKey(APPEAL_BUSINESS_KEY_PREFIX + appeal.getId());
            processReq.setVariables(Map.of("appealId", appeal.getId(), "leadId", leadId,
                    "roundNo", roundNo, "reviewStage", appeal.getReviewStage()));
            processReq.setStartUserSelectAssignees(Map.of(APPEAL_TASK_DEFINITION_KEY, reviewers));
            appeal.setProcessInstanceId(processInstanceApi.createProcessInstance(userId, processReq));
        } catch (RuntimeException ex) {
            throw exception(LEAD_APPEAL_PROCESS_UNAVAILABLE);
        }
        appealMapper.updateById(appeal);
        BusinessEventDO event = addEvent(EVENT_LEAD_APPEAL_SUBMITTED, lead, userId, appeal,
                STATUS_INVALID, appeal.getStatus(), appeal.getReason(), appeal.getEvidenceRefs(), now,
                reqVO.getIdempotencyKey());
        Map<String, Object> context = appealContext(lead, appeal);
        context.put("appeal.reviewerUserIds", reviewers);
        notifyEventPublisher.publish(cn.iocoder.yudao.module.zsjos.enums.LeadNotifySceneConstants.APPEAL_SUBMITTED,
                leadId, event.getIdempotencyKey(), userId, now, context);
        return appeal.getId();
    }

    @Override
    public PageResult<LeadAppealRespVO> getInboxPage(LeadAppealPageReqVO reqVO, Long userId) {
        BpmTaskPageReqDTO taskReq = new BpmTaskPageReqDTO();
        taskReq.setPageNo(reqVO.getPageNo());
        taskReq.setPageSize(reqVO.getPageSize());
        taskReq.setProcessDefinitionKey(APPEAL_PROCESS_DEFINITION_KEY);
        PageResult<BpmTaskRespDTO> tasks = Boolean.TRUE.equals(reqVO.getHandled())
                ? processTaskApi.getDoneTaskPage(userId, taskReq) : processTaskApi.getTodoTaskPage(userId, taskReq);
        List<LeadAppealRespVO> result = new ArrayList<>();
        for (BpmTaskRespDTO task : tasks.getList()) {
            Long appealId = parseAppealId(task.getBusinessKey());
            LeadAppealDO appeal = appealId == null ? null : appealMapper.selectById(appealId);
            if (appeal == null) continue;
            LeadDO lead = leadMapper.selectById(appeal.getLeadId());
            if (lead == null || !canReview(appeal, lead, userId)) continue;
            result.add(convert(appeal, lead, task.getId()));
        }
        return new PageResult<>(result, tasks.getTotal());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void overturn(Long appealId, Long userId, LeadAppealDecisionReqVO reqVO) {
        decide(appealId, userId, reqVO, true);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void uphold(Long appealId, Long userId, LeadAppealDecisionReqVO reqVO) {
        decide(appealId, userId, reqVO, false);
    }

    private void decide(Long appealId, Long userId, LeadAppealDecisionReqVO reqVO, boolean overturn) {
        LeadAppealDO appeal = appealMapper.selectByIdForUpdate(appealId, TenantContextHolder.getRequiredTenantId());
        if (appeal == null) throw exception(LEAD_APPEAL_NOT_EXISTS);
        LeadAppealDO duplicate = appealMapper.selectByDecisionIdempotencyKey(reqVO.getIdempotencyKey());
        if (duplicate != null) {
            if (Objects.equals(duplicate.getId(), appealId) && Objects.equals(duplicate.getReviewerUserId(), userId)) return;
            throw exception(LEAD_APPEAL_IDEMPOTENCY_CONFLICT);
        }
        if (!isReviewing(appeal.getStatus())) throw exception(LEAD_APPEAL_ALREADY_HANDLED);
        LeadDO lead = requireLeadForUpdate(appeal.getLeadId());
        if (!canReview(appeal, lead, userId)) throw exception(LEAD_APPEAL_PERMISSION_DENIED);
        BpmTaskRespDTO task;
        try {
            task = processTaskApi.getTodoTask(userId, reqVO.getTaskId());
        } catch (RuntimeException ex) {
            throw exception(LEAD_APPEAL_ALREADY_HANDLED);
        }
        if (!Objects.equals(task.getProcessInstanceId(), appeal.getProcessInstanceId())
                || !Objects.equals(task.getBusinessKey(), APPEAL_BUSINESS_KEY_PREFIX + appealId)) {
            throw exception(LEAD_APPEAL_PERMISSION_DENIED);
        }
        String evidenceJson = buildEvidenceJson(reqVO.getAttachments(), userId);
        List<String> bpmAttachments = parseEvidence(evidenceJson).stream().map(EvidenceRef::getFileUrl)
                .filter(Objects::nonNull).toList();
        BpmTaskDecisionReqDTO decision = new BpmTaskDecisionReqDTO();
        decision.setTaskId(reqVO.getTaskId());
        decision.setReason(reqVO.getReason().trim());
        decision.setAttachments(bpmAttachments);
        if (overturn) processTaskApi.approveTask(userId, decision); else processTaskApi.rejectTask(userId, decision);

        LocalDateTime now = LocalDateTime.now();
        appeal.setStatus(overturn ? APPEAL_STATUS_OVERTURNED : APPEAL_STATUS_UPHELD);
        appeal.setReviewerUserId(userId);
        appeal.setDecisionReason(reqVO.getReason().trim());
        appeal.setDecisionEvidenceRefs(evidenceJson);
        appeal.setDecisionIdempotencyKey(reqVO.getIdempotencyKey());
        appeal.setDecidedAt(now);
        appealMapper.updateById(appeal);
        if (overturn) {
            lead.setStatus(STATUS_VALID);
            lead.setInvalidReason(null);
            lead.setInvalidReasonLabelSnapshot(null);
            lead.setInvalidDescription(null);
            lead.setInvalidEvidenceRefs(null);
            lead.setAppealDeadlineAt(null);
            lead.setQualifiedByUserId(userId);
            lead.setQualifiedAt(now);
            leadMapper.updateById(lead);
        }
        String eventType = overturn ? EVENT_LEAD_APPEAL_OVERTURNED : EVENT_LEAD_APPEAL_UPHELD;
        String scene = overturn ? cn.iocoder.yudao.module.zsjos.enums.LeadNotifySceneConstants.APPEAL_OVERTURNED
                : cn.iocoder.yudao.module.zsjos.enums.LeadNotifySceneConstants.APPEAL_UPHELD;
        BusinessEventDO event = addEvent(eventType, lead, userId, appeal, reviewingStatus(appeal.getRoundNo()),
                appeal.getStatus(), appeal.getDecisionReason(), appeal.getDecisionEvidenceRefs(), now,
                reqVO.getIdempotencyKey());
        notifyEventPublisher.publish(scene, lead.getId(), event.getIdempotencyKey(), userId, now,
                appealContext(lead, appeal));
    }

    @Override
    public LeadAttachmentUploadRespVO upload(MultipartFile file) throws IOException {
        return attachmentService.upload(file);
    }

    private List<Long> resolveReviewers(int roundNo, LeadDO lead) {
        if (roundNo == 1) {
            AdminUserRespDTO owner = adminUserApi.getUser(lead.getOwnerUserId());
            DeptRespDTO dept = owner == null || owner.getDeptId() == null ? null : deptApi.getDept(owner.getDeptId());
            Long leader = dept == null ? null : dept.getLeaderUserId();
            AdminUserRespDTO user = leader == null ? null : adminUserApi.getUser(leader);
            if (user == null || !CommonStatusEnum.ENABLE.getStatus().equals(user.getStatus())
                    || !permissionApi.hasAnyPermissions(leader, PERMISSION_APPEAL_REVIEW_SALES_MANAGER)) {
                throw exception(LEAD_APPEAL_REVIEWER_NOT_CONFIGURED);
            }
            return List.of(leader);
        }
        if (roundNo == 2) {
            List<Long> users = roleUsers(ROLE_QUALITY_MANAGER, ROLE_QUALITY_SPECIALIST);
            if (users.isEmpty()) throw exception(LEAD_APPEAL_REVIEWER_NOT_CONFIGURED);
            return users;
        }
        List<Long> users = roleUsers(ROLE_CHAIRMAN);
        if (users.size() != 1) throw exception(LEAD_APPEAL_CHAIRMAN_INVALID);
        return users;
    }

    private List<Long> roleUsers(String... codes) {
        Set<Long> roleIds = new LinkedHashSet<>();
        for (String code : codes) {
            RoleRespDTO role = roleApi.getRoleByCode(code);
            if (role != null && CommonStatusEnum.ENABLE.getStatus().equals(role.getStatus())) roleIds.add(role.getId());
        }
        if (roleIds.isEmpty()) return List.of();
        return adminUserApi.getUserList(permissionApi.getUserRoleIdListByRoleIds(roleIds)).stream()
                .filter(user -> CommonStatusEnum.ENABLE.getStatus().equals(user.getStatus()))
                .map(AdminUserRespDTO::getId).distinct().sorted().toList();
    }

    private boolean canReview(LeadAppealDO appeal, LeadDO lead, Long userId) {
        if (APPEAL_STAGE_SALES_MANAGER.equals(appeal.getReviewStage())) {
            AdminUserRespDTO owner = adminUserApi.getUser(lead.getOwnerUserId());
            DeptRespDTO dept = owner == null || owner.getDeptId() == null ? null : deptApi.getDept(owner.getDeptId());
            return dept != null && Objects.equals(dept.getLeaderUserId(), userId)
                    && permissionApi.hasAnyPermissions(userId, PERMISSION_APPEAL_REVIEW_SALES_MANAGER);
        }
        if (APPEAL_STAGE_QUALITY.equals(appeal.getReviewStage())) {
            return permissionApi.hasAnyPermissions(userId, PERMISSION_APPEAL_REVIEW_QUALITY);
        }
        return APPEAL_STAGE_CHAIRMAN.equals(appeal.getReviewStage())
                && permissionApi.hasAnyPermissions(userId, PERMISSION_APPEAL_REVIEW_CHAIRMAN);
    }

    private String buildEvidenceJson(List<LeadAttachmentReqVO> attachments, Long userId) {
        if (attachments == null || attachments.isEmpty()) return null;
        Map<Long, FileInfoRespDTO> files = attachmentService.validateReferences(attachments, userId);
        List<EvidenceRef> refs = new ArrayList<>();
        int sort = 0;
        for (LeadAttachmentReqVO item : attachments) {
            FileInfoRespDTO file = files.get(item.getInfraFileId());
            refs.add(new EvidenceRef(file.getId(), file.getUrl(), file.getName(), file.getType(), file.getSize(), sort++));
        }
        return JsonUtils.toJsonString(refs);
    }

    private LeadAppealRespVO convert(LeadAppealDO source, LeadDO lead, String taskId) {
        LeadAppealRespVO result = new LeadAppealRespVO();
        result.setId(source.getId()); result.setLeadId(source.getLeadId()); result.setLeadName(lead.getSubmittedName());
        result.setRoundNo(source.getRoundNo()); result.setReviewStage(source.getReviewStage()); result.setStatus(source.getStatus());
        result.setApplicantUserId(source.getApplicantUserId()); result.setApplicantUserName(userName(source.getApplicantUserId()));
        result.setReason(source.getReason()); result.setEvidence(toEvidenceVO(source.getEvidenceRefs()));
        result.setInvalidReasonSnapshot(source.getInvalidReasonSnapshot());
        result.setInvalidDescriptionSnapshot(source.getInvalidDescriptionSnapshot());
        result.setInvalidEvidenceSnapshot(toEvidenceVO(source.getInvalidEvidenceRefsSnapshot()));
        result.setProcessInstanceId(source.getProcessInstanceId()); result.setTaskId(taskId);
        result.setReviewerUserId(source.getReviewerUserId()); result.setReviewerUserName(userName(source.getReviewerUserId()));
        result.setDecisionReason(source.getDecisionReason()); result.setDecisionEvidence(toEvidenceVO(source.getDecisionEvidenceRefs()));
        result.setSubmittedAt(source.getSubmittedAt()); result.setDecidedAt(source.getDecidedAt());
        result.setCanSubmitNextRound(APPEAL_STATUS_UPHELD.equals(source.getStatus()) && source.getRoundNo() < 3);
        return result;
    }

    private List<LeadAppealRespVO.EvidenceVO> toEvidenceVO(String json) {
        List<EvidenceRef> refs = parseEvidence(json);
        Map<Long, String> urls = refs.isEmpty() ? Map.of() : fileApi.presignGetUrls(
                refs.stream().map(EvidenceRef::getInfraFileId).filter(Objects::nonNull).toList(),
                ATTACHMENT_URL_EXPIRATION_SECONDS);
        return refs.stream().map(ref -> {
            LeadAppealRespVO.EvidenceVO vo = new LeadAppealRespVO.EvidenceVO();
            vo.setInfraFileId(ref.getInfraFileId()); vo.setFileUrl(urls.getOrDefault(ref.getInfraFileId(), ref.getFileUrl()));
            vo.setOriginalName(ref.getOriginalName()); vo.setContentType(ref.getContentType());
            vo.setFileSize(ref.getFileSize()); vo.setSort(ref.getSort()); return vo;
        }).toList();
    }

    private List<EvidenceRef> parseEvidence(String json) {
        return json == null ? List.of() : JsonUtils.parseArray(json, EvidenceRef.class);
    }

    private String userName(Long id) {
        AdminUserRespDTO user = id == null ? null : adminUserApi.getUser(id);
        return user == null ? null : user.getNickname();
    }

    private BusinessEventDO addEvent(String type, LeadDO lead, Long operatorUserId, LeadAppealDO appeal,
                                     String fromStatus, String toStatus, String reason, String evidence,
                                     LocalDateTime occurredAt, String idempotencyKey) {
        BusinessEventDO event = new BusinessEventDO();
        event.setEventType(type);
        event.setAggregateType(BIZ_TYPE_LEAD);
        event.setAggregateId(lead.getId());
        event.setOperatorUserId(operatorUserId);
        event.setFromStatus(fromStatus);
        event.setToStatus(toStatus);
        event.setReason(reason);
        event.setEvidenceRefs(evidence);
        event.setRelatedObjectRefs(JsonUtils.toJsonString(Map.of("appealId", appeal.getId(),
                "roundNo", appeal.getRoundNo(), "processInstanceId", appeal.getProcessInstanceId())));
        event.setOccurredAt(occurredAt);
        event.setIdempotencyKey(type + ":" + idempotencyKey);
        eventMapper.insert(event);
        return event;
    }

    private Map<String, Object> appealContext(LeadDO lead, LeadAppealDO appeal) {
        Map<String, Object> context = new LinkedHashMap<>();
        context.put("submitterUserId", lead.getSourceUserId());
        context.put("ownerUserId", lead.getOwnerUserId());
        context.put("appeal.id", appeal.getId());
        context.put("appeal.roundNo", appeal.getRoundNo());
        context.put("appeal.stage", appeal.getReviewStage());
        context.put("appeal.reason", appeal.getReason());
        context.put("appeal.decisionReason", appeal.getDecisionReason());
        return context;
    }

    private Long parseAppealId(String businessKey) {
        if (businessKey == null || !businessKey.startsWith(APPEAL_BUSINESS_KEY_PREFIX)) return null;
        try { return Long.valueOf(businessKey.substring(APPEAL_BUSINESS_KEY_PREFIX.length())); }
        catch (NumberFormatException ex) { return null; }
    }

    private LeadDO requireLead(Long id) {
        LeadDO lead = leadMapper.selectById(id);
        if (lead == null) throw exception(LEAD_NOT_EXISTS);
        return lead;
    }

    private LeadDO requireLeadForUpdate(Long id) {
        LeadDO lead = leadMapper.selectByIdForUpdate(id, TenantContextHolder.getRequiredTenantId());
        if (lead == null) throw exception(LEAD_NOT_EXISTS);
        return lead;
    }

    private String stage(int round) { return round == 1 ? APPEAL_STAGE_SALES_MANAGER : round == 2 ? APPEAL_STAGE_QUALITY : APPEAL_STAGE_CHAIRMAN; }
    private String reviewingStatus(int round) { return round == 1 ? APPEAL_STATUS_SALES_MANAGER_REVIEWING : round == 2 ? APPEAL_STATUS_QUALITY_REVIEWING : APPEAL_STATUS_CHAIRMAN_REVIEWING; }
    private boolean isReviewing(String status) { return Set.of(APPEAL_STATUS_SALES_MANAGER_REVIEWING, APPEAL_STATUS_QUALITY_REVIEWING, APPEAL_STATUS_CHAIRMAN_REVIEWING).contains(status); }

    @Data @NoArgsConstructor @AllArgsConstructor
    private static class EvidenceRef {
        private Long infraFileId;
        private String fileUrl;
        private String originalName;
        private String contentType;
        private Long fileSize;
        private Integer sort;
    }
}
