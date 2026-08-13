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
import static cn.iocoder.yudao.module.zsjos.enums.LeadNotifySceneConstants.ACTIVATED;
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
    @Resource private LeadObjectPermissionService permissionService;
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
        boolean all = securityFrameworkService.hasPermission("zsjos:lead-duplicate-review:manage-all");
        Set<Long> managed = all ? Set.of() : permissionService.getManagedUserIds(reviewerUserId);
        return assignmentService.getEligibleSalesUsers().stream()
                .filter(user -> all || managed.contains(user.getId())).toList();
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
        if (request.getMatchedLeadId() == null || request.getSelectedSalesUserId() == null) {
            throw exception(LEAD_DUPLICATE_REVIEW_RESULT_INVALID);
        }
        if (getSalesCandidates(reviewerUserId).stream()
                .noneMatch(user -> Objects.equals(user.getId(), request.getSelectedSalesUserId()))) {
            throw exception(LEAD_DUPLICATE_REVIEW_SALES_INVALID);
        }
        LeadDO lead = leadMapper.selectByIdForUpdate(request.getMatchedLeadId(), TenantContextHolder.getRequiredTenantId());
        if (lead == null || !Set.of(STATUS_INVALID, STATUS_CLOSED).contains(lead.getStatus())) {
            throw exception(LEAD_DUPLICATE_REVIEW_RESULT_INVALID);
        }
        PersonDO person = personMapper.selectById(lead.getPersonId());
        String previousAssignmentStatus = lead.getAssignmentStatus();
        before.put("person", person); before.put("lead", lead);
        before.put("products", productMapper.selectListByLeadId(lead.getId()));
        before.put("attachments", attachmentMapper.selectListByLeadId(lead.getId()));
        personIdentityWriteService.update(person.getId(), submission.getName().trim(),
                submission.getMobile(), submission.getWechatId());
        lead.setSubmittedName(submission.getName().trim()); lead.setSubmittedMobile(submission.getMobile());
        lead.setSubmittedWechatId(submission.getWechatId()); lead.setProvinceCode(submission.getProvinceCode());
        lead.setCityCode(submission.getCityCode()); lead.setLeadCategory(submission.getLeadCategory());
        lead.setRemark(submission.getRemark()); lead.setStatus(STATUS_SUBMITTED); lead.setAssignmentStatus(ASSIGNMENT_OWNED);
        lead.setOwnerUserId(request.getSelectedSalesUserId()); lead.setOwnershipStartedAt(now);
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
        assignment.setFromOwnerUserId(beforeOwner(before)); assignment.setToOwnerUserId(request.getSelectedSalesUserId());
        assignment.setOperatorUserId(reviewerUserId); assignment.setReason(request.getOpinion().trim()); assignment.setOccurredAt(now);
        assignmentHistoryMapper.insert(assignment);
        lead.setCurrentAssignmentHistoryId(assignment.getId());
        LocalDateTime firstDue = lifecycleTaskService.createFirstFollowUpTask(lead.getId(), request.getSelectedSalesUserId(),
                assignment.getId(), now, EVENT_LEAD_RESTORED, previousAssignmentStatus);
        lead.setCurrentAssignmentFirstFollowUpDeadlineAt(firstDue);
        leadMapper.updateById(lead);
        OpportunityDO opportunity = opportunityMapper.selectByLeadId(lead.getId());
        if (opportunity != null && !OPPORTUNITY_STATUS_LOST.equals(opportunity.getStatus())) {
            opportunity.setStatus(OPPORTUNITY_STATUS_LOST); opportunity.setLostAt(now);
            opportunity.setLostReason("重复客资复核重新激活，等待重新判有效"); opportunityMapper.updateById(opportunity);
        }
        after.put("person", personMapper.selectById(person.getId())); after.put("lead", leadMapper.selectById(lead.getId()));
    }

    private Long beforeOwner(Map<String, Object> before) {
        Object value = before.get("lead");
        return value instanceof LeadDO lead ? lead.getOwnerUserId() : null;
    }

    private void notifyOwner(LeadDuplicateReviewDecisionReqVO request, Long reviewerUserId, LocalDateTime now,
                             Map<String, Object> before, Map<String, Object> after) {
        if (request.getMatchedLeadId() == null) throw exception(LEAD_DUPLICATE_REVIEW_RESULT_INVALID);
        LeadDO lead = leadMapper.selectById(request.getMatchedLeadId());
        if (lead == null || lead.getOwnerUserId() == null) throw exception(LEAD_DUPLICATE_REVIEW_RESULT_INVALID);
        before.put("lead", lead); after.put("unchanged", true);
        notifyEventPublisher.publish(ACTIVATED, lead.getId(), "duplicate-review-notify:" + UUID.randomUUID(),
                reviewerUserId, now, Map.of("ownerUserId", lead.getOwnerUserId(), "submitterUserId", lead.getSourceUserId()));
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
