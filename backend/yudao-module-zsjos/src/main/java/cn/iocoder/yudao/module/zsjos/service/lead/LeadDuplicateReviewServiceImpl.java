package cn.iocoder.yudao.module.zsjos.service.lead;

import cn.iocoder.yudao.framework.common.enums.CommonStatusEnum;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.security.core.service.SecurityFrameworkService;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.infra.api.file.dto.FileInfoRespDTO;
import cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.assignment.LeadAssignmentUserRespVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.duplicate.*;
import cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.submission.*;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.*;
import cn.iocoder.yudao.module.zsjos.dal.mysql.lead.*;
import cn.iocoder.yudao.module.zsjos.framework.permission.ZsjosPermission;
import cn.iocoder.yudao.module.zsjos.service.lead.product.LeadProductSnapshot;
import cn.iocoder.yudao.module.zsjos.service.product.ZsjosProductSkuService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.zsjos.enums.LeadConstants.*;
import static cn.iocoder.yudao.module.zsjos.enums.LeadNotifySceneConstants.DUPLICATE_OWNER_REMINDER;
import static cn.iocoder.yudao.module.zsjos.enums.LeadNotifySceneConstants.DUPLICATE_REACTIVATED;
import static cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants.*;

@Service
public class LeadDuplicateReviewServiceImpl implements LeadDuplicateReviewService {
    @Resource private LeadDuplicateReviewMapper reviewMapper;
    @Resource private PersonMapper personMapper;
    @Resource private LeadMapper leadMapper;
    @Resource private OpportunityMapper opportunityMapper;
    @Resource private LeadIntendedProductMapper productMapper;
    @Resource private LeadAttachmentMapper attachmentMapper;
    @Resource private LeadPublicSeaRecordMapper publicSeaRecordMapper;
    @Resource private LeadAssignmentHistoryMapper assignmentHistoryMapper;
    @Resource private LeadSubmissionServiceImpl submissionService;
    @Resource private LeadAssignmentService assignmentService;
    @Resource private LeadAttachmentService attachmentService;
    @Resource private ZsjosProductSkuService productSkuService;
    @Resource private LeadLifecycleTaskService lifecycleTaskService;
    @Resource private LeadNotifyEventPublisher notifyEventPublisher;
    @Resource private SecurityFrameworkService securityFrameworkService;
    @Resource private PersonIdentityWriteService personIdentityWriteService;

    @Override
    public PageResult<LeadDuplicateReviewRespVO> getPage(LeadDuplicateReviewPageReqVO request) {
        PageResult<LeadDuplicateReviewDO> page = reviewMapper.selectPage(request, request.getStatus());
        return new PageResult<>(page.getList().stream().map(this::toResponse).toList(), page.getTotal());
    }

    @Override
    @ZsjosPermission(bizType = BIZ_TYPE_LEAD_DUPLICATE_REVIEW, bizId = "#id", action = "read")
    public LeadDuplicateReviewRespVO get(Long id) {
        LeadDuplicateReviewDO review = reviewMapper.selectById(id);
        if (review == null) throw exception(LEAD_DUPLICATE_REVIEW_NOT_EXISTS);
        return toResponse(review);
    }

    @Override
    public List<LeadAssignmentUserRespVO> getSalesCandidates(Long reviewerUserId) {
        if (!securityFrameworkService.hasPermission("zsjos:lead-duplicate-review:process")) {
            throw exception(LEAD_DUPLICATE_REVIEW_PERMISSION_DENIED);
        }
        return assignmentService.getEligibleSalesUsers().stream()
                .toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @ZsjosPermission(bizType = BIZ_TYPE_LEAD_DUPLICATE_REVIEW, bizId = "#id", action = "process")
    public void decide(Long id, LeadDuplicateReviewDecisionReqVO request, Long reviewerUserId) {
        LeadDuplicateReviewDO review = reviewMapper.selectByIdForUpdate(id, TenantContextHolder.getRequiredTenantId());
        if (review == null) throw exception(LEAD_DUPLICATE_REVIEW_NOT_EXISTS);
        if ("completed".equals(review.getStatus())) {
            if (Objects.equals(review.getDecisionIdempotencyKey(), request.getIdempotencyKey())
                    && Objects.equals(review.getReviewerUserId(), reviewerUserId)) return;
            throw exception(LEAD_DUPLICATE_REVIEW_HANDLED);
        }
        LeadCreateReqVO submission = JsonUtils.parseObject(review.getSubmissionSnapshot(), LeadCreateReqVO.class);
        requireSelectedCandidate(review, request);
        if ("reactivate_lead".equals(request.getResultType()) && request.getSelectedSalesUserId() == null) {
            throw exception(LEAD_DUPLICATE_REVIEW_RESULT_INVALID);
        }
        LocalDateTime now = LocalDateTime.now();
        Map<String, Object> before = new LinkedHashMap<>();
        Map<String, Object> after = new LinkedHashMap<>();
        switch (request.getResultType()) {
            case "new_person" -> {
                LeadCreateRespVO result = submissionService.createApprovedFromReview(submission,
                        review.getSubmitterUserId(), null, review.getSubmissionSourceType(), review.getSubmissionPartnerId());
                after.put("personId", leadMapper.selectById(result.getLeadId()).getPersonId());
                after.put("leadId", result.getLeadId());
            }
            case "reuse_person" -> {
                PersonDO person = requirePersonWithoutLead(request.getMatchedPersonId());
                before.put("person", person);
                LeadCreateRespVO result = submissionService.createApprovedFromReview(submission,
                        review.getSubmitterUserId(), person.getId(), review.getSubmissionSourceType(), review.getSubmissionPartnerId());
                after.put("person", personMapper.selectById(person.getId()));
                after.put("leadId", result.getLeadId());
            }
            case "reactivate_lead" -> reactivate(request, submission, review.getSubmitterUserId(), reviewerUserId,
                    now, before, after);
            case "notify_owner" -> notifyOwner(request, reviewerUserId, now, before, after);
            default -> throw exception(LEAD_DUPLICATE_REVIEW_RESULT_INVALID);
        }
        Map<Long, FileInfoRespDTO> files = attachmentService.validateReferences(request.getAttachments(), reviewerUserId);
        review.setStatus("completed");
        review.setResultType(request.getResultType());
        review.setMatchedPersonId(request.getMatchedPersonId());
        review.setMatchedLeadId(request.getMatchedLeadId());
        review.setReviewOpinion(request.getOpinion().trim());
        review.setReviewAttachments(JsonUtils.toJsonString(files.values().stream().map(file -> Map.of(
                "infraFileId", file.getId(), "name", file.getName(), "type", file.getType(), "size", file.getSize())).toList()));
        review.setSelectedSalesUserId(request.getSelectedSalesUserId());
        review.setReviewerUserId(reviewerUserId);
        review.setReviewedAt(now);
        review.setBeforeSnapshot(JsonUtils.toJsonString(before));
        review.setAfterSnapshot(JsonUtils.toJsonString(after));
        review.setDecisionIdempotencyKey(request.getIdempotencyKey());
        review.setVersion(review.getVersion() + 1);
        reviewMapper.updateById(review);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public LeadCreateRespVO resolveAutomatically(Long id, Long matchedLeadId, Long actorUserId) {
        LeadDuplicateReviewDO review = reviewMapper.selectByIdForUpdate(id, TenantContextHolder.getRequiredTenantId());
        if (review == null) throw exception(LEAD_DUPLICATE_REVIEW_NOT_EXISTS);
        if ("completed".equals(review.getStatus())) {
            LeadDO completedLead = leadMapper.selectById(review.getMatchedLeadId());
            if (completedLead == null) throw exception(LEAD_DUPLICATE_REVIEW_RESULT_INVALID);
            return "reactivate_lead".equals(review.getResultType())
                    ? LeadCreateRespVO.activated(completedLead.getId(), completedLead.getLeadNo(), completedLead.getAssignmentStatus())
                    : LeadCreateRespVO.duplicateAutoClosed(completedLead.getId(), completedLead.getLeadNo(), completedLead.getStatus());
        }
        LeadDO matchedLead = leadMapper.selectByIdForUpdate(matchedLeadId,
                TenantContextHolder.getRequiredTenantId());
        if (matchedLead == null) throw exception(LEAD_DUPLICATE_REVIEW_RESULT_INVALID);
        LeadDuplicateReviewDecisionReqVO request = new LeadDuplicateReviewDecisionReqVO();
        boolean reactivatable = Set.of(STATUS_INVALID, STATUS_CLOSED).contains(matchedLead.getStatus());
        request.setResultType(reactivatable ? "reactivate_lead" : "notify_owner");
        request.setMatchedPersonId(matchedLead.getPersonId());
        request.setMatchedLeadId(matchedLeadId);
        request.setOpinion("系统自动判重：选择最近提交的历史客资");
        request.setIdempotencyKey("auto-duplicate:" + review.getSubmissionIdempotencyKey());
        LeadCreateReqVO submission = JsonUtils.parseObject(review.getSubmissionSnapshot(), LeadCreateReqVO.class);
        Map<String, Object> before = new LinkedHashMap<>();
        Map<String, Object> after = new LinkedHashMap<>();
        LocalDateTime now = LocalDateTime.now();
        if (reactivatable) {
            reactivateLocked(request, submission, review.getSubmitterUserId(), actorUserId, now,
                    before, after, matchedLead);
        } else {
            notifyOwnerLocked(request, actorUserId, now, before, after, matchedLead);
        }
        review.setStatus("completed");
        review.setResultType(request.getResultType());
        review.setMatchedPersonId(matchedLead.getPersonId());
        review.setMatchedLeadId(matchedLeadId);
        review.setReviewOpinion(request.getOpinion());
        review.setReviewAttachments("[]");
        review.setReviewerUserId(actorUserId);
        review.setReviewedAt(now);
        review.setBeforeSnapshot(JsonUtils.toJsonString(before));
        review.setAfterSnapshot(JsonUtils.toJsonString(after));
        review.setDecisionIdempotencyKey(request.getIdempotencyKey());
        review.setVersion(review.getVersion() + 1);
        reviewMapper.updateById(review);
        LeadDO resolved = leadMapper.selectById(matchedLeadId);
        return reactivatable
                ? LeadCreateRespVO.activated(resolved.getId(), resolved.getLeadNo(), resolved.getAssignmentStatus())
                : LeadCreateRespVO.duplicateAutoClosed(resolved.getId(), resolved.getLeadNo(), resolved.getStatus());
    }

    @SuppressWarnings("rawtypes")
    private void requireSelectedCandidate(LeadDuplicateReviewDO review, LeadDuplicateReviewDecisionReqVO request) {
        if ("new_person".equals(request.getResultType())) return;
        List<Map> candidates = JsonUtils.parseArray(review.getCandidateSnapshot(), Map.class);
        boolean matched = candidates.stream().anyMatch(candidate -> {
            Long personId = number(candidate.get("personId"));
            Long leadId = number(candidate.get("leadId"));
            if ("reuse_person".equals(request.getResultType())) {
                return Objects.equals(personId, request.getMatchedPersonId()) && leadId == null;
            }
            return Objects.equals(leadId, request.getMatchedLeadId());
        });
        if (!matched) throw exception(LEAD_DUPLICATE_REVIEW_RESULT_INVALID);
    }

    private Long number(Object value) {
        return value instanceof Number number ? number.longValue() : null;
    }

    private PersonDO requirePersonWithoutLead(Long personId) {
        if (personId == null) throw exception(LEAD_DUPLICATE_REVIEW_RESULT_INVALID);
        PersonDO person = personMapper.selectById(personId);
        if (person == null || leadMapper.selectLatestByPersonId(personId) != null) {
            throw exception(LEAD_DUPLICATE_REVIEW_RESULT_INVALID);
        }
        return person;
    }

    private void reactivate(LeadDuplicateReviewDecisionReqVO request, LeadCreateReqVO submission,
                            Long submitterUserId, Long reviewerUserId, LocalDateTime now, Map<String, Object> before,
                            Map<String, Object> after) {
        if (request.getMatchedLeadId() == null) {
            throw exception(LEAD_DUPLICATE_REVIEW_RESULT_INVALID);
        }
        if (request.getSelectedSalesUserId() != null && getSalesCandidates(reviewerUserId).stream()
                .noneMatch(user -> Objects.equals(user.getId(), request.getSelectedSalesUserId()))) {
            throw exception(LEAD_DUPLICATE_REVIEW_SALES_INVALID);
        }
        LeadDO lead = leadMapper.selectByIdForUpdate(request.getMatchedLeadId(), TenantContextHolder.getRequiredTenantId());
        reactivateLocked(request, submission, submitterUserId, reviewerUserId, now, before, after, lead);
    }

    private void reactivateLocked(LeadDuplicateReviewDecisionReqVO request, LeadCreateReqVO submission,
                                  Long submitterUserId, Long reviewerUserId, LocalDateTime now,
                                  Map<String, Object> before, Map<String, Object> after, LeadDO lead) {
        if (lead == null || !Set.of(STATUS_INVALID, STATUS_CLOSED).contains(lead.getStatus())) {
            throw exception(LEAD_DUPLICATE_REVIEW_RESULT_INVALID);
        }
        Long previousOwnerUserId = lead.getOwnerUserId();
        boolean previousOwnerValid = previousOwnerUserId != null && assignmentService.getEligibleSalesUsers().stream()
                .anyMatch(user -> Objects.equals(user.getId(), previousOwnerUserId));
        Long targetOwnerUserId = request.getSelectedSalesUserId() == null
                ? (previousOwnerValid ? previousOwnerUserId : null) : request.getSelectedSalesUserId();
        PersonDO person = personMapper.selectById(lead.getPersonId());
        String previousAssignmentStatus = lead.getAssignmentStatus();
        before.put("person", deepSnapshot(person)); before.put("lead", deepSnapshot(lead));
        before.put("products", deepSnapshot(productMapper.selectListByLeadId(lead.getId())));
        before.put("attachments", deepSnapshot(attachmentMapper.selectListByLeadId(lead.getId())));
        personIdentityWriteService.update(person.getId(), submission.getName().trim(),
                submission.getMobile(), submission.getWechatId());
        lead.setSubmittedName(submission.getName().trim()); lead.setSubmittedMobile(submission.getMobile());
        lead.setSubmittedWechatId(submission.getWechatId()); lead.setProvinceCode(submission.getProvinceCode());
        lead.setCityCode(submission.getCityCode()); lead.setLeadCategory(submission.getLeadCategory());
        lead.setRemark(submission.getRemark()); lead.setStatus(STATUS_SUBMITTED);
        lead.setAssignmentStatus(targetOwnerUserId == null ? ASSIGNMENT_PUBLIC_POOL : ASSIGNMENT_OWNED);
        lead.setOwnerUserId(targetOwnerUserId); lead.setOwnershipStartedAt(targetOwnerUserId == null ? null : now);
        lead.setPublicPoolAt(targetOwnerUserId == null ? now : null);
        lead.setQualifiedByUserId(null); lead.setQualifiedAt(null); lead.setValidDescription(null);
        lead.setInvalidReason(null); lead.setInvalidReasonLabelSnapshot(null); lead.setInvalidDescription(null);
        lead.setInvalidEvidenceRefs(null); lead.setClosedAt(null); lead.setCloseReason(null); lead.setSuspendedAt(null);
        lead.setCurrentAssignmentFirstFollowUpAt(null); lead.setNextFollowUpAt(null);
        leadMapper.updateById(lead);
        productMapper.deleteByLeadId(lead.getId()); attachmentMapper.deleteByLeadId(lead.getId());
        insertProductsAndAttachments(lead.getId(), submission, submitterUserId);
        publicSeaRecordMapper.deleteByLeadId(lead.getId());
        LeadAssignmentHistoryDO assignment = new LeadAssignmentHistoryDO();
        assignment.setLeadId(lead.getId()); assignment.setActionType("duplicate_review_reactivate");
        assignment.setFromOwnerUserId(previousOwnerUserId); assignment.setToOwnerUserId(targetOwnerUserId);
        assignment.setOperatorUserId(reviewerUserId); assignment.setReason(request.getOpinion().trim()); assignment.setOccurredAt(now);
        assignmentHistoryMapper.insert(assignment);
        lead.setCurrentAssignmentHistoryId(assignment.getId());
        lifecycleTaskService.cancelFirstFollowUpTasks(lead.getId(), now, "重复客资重新激活");
        lifecycleTaskService.cancelFollowUpReminders(lead.getId(), now, "重复客资重新激活");
        if (targetOwnerUserId != null) {
            LocalDateTime firstDue = lifecycleTaskService.createFirstFollowUpTask(lead.getId(), targetOwnerUserId,
                    assignment.getId(), now, EVENT_LEAD_RESTORED, previousAssignmentStatus);
            lead.setCurrentAssignmentFirstFollowUpDeadlineAt(firstDue);
        } else {
            lead.setCurrentAssignmentFirstFollowUpDeadlineAt(null);
        }
        leadMapper.updateById(lead);
        OpportunityDO opportunity = opportunityMapper.selectByLeadId(lead.getId());
        if (opportunity != null && !OPPORTUNITY_STATUS_LOST.equals(opportunity.getStatus())) {
            opportunity.setStatus(OPPORTUNITY_STATUS_LOST); opportunity.setLostAt(now);
            opportunity.setLostReason("重复客资复核重新激活，等待重新判有效"); opportunityMapper.updateById(opportunity);
        }
        Map<String, Object> notification = new LinkedHashMap<>();
        if (targetOwnerUserId != null) notification.put("ownerUserId", targetOwnerUserId);
        if (previousOwnerValid) notification.put("previousOwnerUserId", previousOwnerUserId);
        if (targetOwnerUserId != null) notification.put("newOwnerUserId", targetOwnerUserId);
        notification.put("submitterUserId", lead.getSourceUserId());
        notification.put("assignment.reason", request.getOpinion().trim());
        notifyEventPublisher.publish(DUPLICATE_REACTIVATED, lead.getId(),
                "duplicate-review-reactivated:" + lead.getId() + ":" + now, reviewerUserId, now, notification);
        after.put("person", personMapper.selectById(person.getId())); after.put("lead", leadMapper.selectById(lead.getId()));
    }

    private Object deepSnapshot(Object value) {
        return JsonUtils.parseObject(JsonUtils.toJsonString(value), Object.class);
    }

    private void notifyOwner(LeadDuplicateReviewDecisionReqVO request, Long reviewerUserId, LocalDateTime now,
                             Map<String, Object> before, Map<String, Object> after) {
        if (request.getMatchedLeadId() == null) throw exception(LEAD_DUPLICATE_REVIEW_RESULT_INVALID);
        LeadDO lead = leadMapper.selectByIdForUpdate(request.getMatchedLeadId(),
                TenantContextHolder.getRequiredTenantId());
        notifyOwnerLocked(request, reviewerUserId, now, before, after, lead);
    }

    private void notifyOwnerLocked(LeadDuplicateReviewDecisionReqVO request, Long reviewerUserId,
                                   LocalDateTime now, Map<String, Object> before,
                                   Map<String, Object> after, LeadDO lead) {
        if (lead == null) throw exception(LEAD_DUPLICATE_REVIEW_RESULT_INVALID);
        before.put("lead", lead); after.put("unchanged", true);
        boolean ownerValid = lead.getOwnerUserId() != null && assignmentService.getEligibleSalesUsers().stream()
                .anyMatch(user -> Objects.equals(user.getId(), lead.getOwnerUserId()));
        if (!ownerValid) {
            after.put("notification", "no_valid_recipient");
            return;
        }
        notifyEventPublisher.publish(DUPLICATE_OWNER_REMINDER, lead.getId(),
                "duplicate-review-notify:" + UUID.randomUUID(), reviewerUserId, now,
                Map.of("ownerUserId", lead.getOwnerUserId()));
    }

    private void insertProductsAndAttachments(Long leadId, LeadCreateReqVO submission, Long reviewerUserId) {
        List<LeadProductReqVO> requested = submission.getEffectiveProducts();
        for (int i = 0; i < requested.size(); i++) {
            LeadProductReqVO item = requested.get(i);
            LeadProductSnapshot snapshot = productSkuService.validateLeadProduct(item.effectiveSpuRef(),
                    Boolean.TRUE.equals(item.getSpuUnknown()), item.getSkuRef(), Boolean.TRUE.equals(item.getSkuUnknown()));
            LeadIntendedProductDO row = new LeadIntendedProductDO();
            row.setLeadId(leadId); row.setProductRef(snapshot.productRef()); row.setProductNameSnapshot(snapshot.name());
            row.setSpuRef(snapshot.productRef()); row.setSpuNameSnapshot(snapshot.name()); row.setSkuRef(snapshot.skuRef());
            row.setSkuNameSnapshot(snapshot.skuName()); row.setSpuUnknown(snapshot.spuUnknown()); row.setSkuUnknown(snapshot.skuUnknown());
            row.setIsPrimary(item.getPrimary()); row.setSort(i); productMapper.insert(row);
        }
        Map<Long, FileInfoRespDTO> files = attachmentService.validateReferences(submission.getAttachments(), reviewerUserId);
        for (int i = 0; i < submission.getAttachments().size(); i++) {
            FileInfoRespDTO file = files.get(submission.getAttachments().get(i).getInfraFileId());
            LeadAttachmentDO row = new LeadAttachmentDO(); row.setLeadId(leadId); row.setInfraFileId(file.getId());
            row.setOriginalName(file.getName()); row.setContentType(file.getType()); row.setFileSize(file.getSize()); row.setSort(i);
            attachmentMapper.insert(row);
        }
    }

    private LeadDuplicateReviewRespVO toResponse(LeadDuplicateReviewDO row) {
        LeadDuplicateReviewRespVO response = new LeadDuplicateReviewRespVO();
        response.setId(row.getId()); response.setStatus(row.getStatus()); response.setSubmitterUserId(row.getSubmitterUserId());
        response.setSubmissionSnapshot(row.getSubmissionSnapshot()); response.setMatchRules(row.getMatchRules());
        response.setCandidateSnapshot(row.getCandidateSnapshot()); response.setResultType(row.getResultType());
        response.setReviewOpinion(row.getReviewOpinion()); response.setSelectedSalesUserId(row.getSelectedSalesUserId());
        response.setReviewAttachments(row.getReviewAttachments()); response.setBeforeSnapshot(row.getBeforeSnapshot());
        response.setAfterSnapshot(row.getAfterSnapshot()); response.setReviewerUserId(row.getReviewerUserId());
        response.setReviewedAt(row.getReviewedAt()); response.setCreateTime(row.getCreateTime());
        return response;
    }
}
