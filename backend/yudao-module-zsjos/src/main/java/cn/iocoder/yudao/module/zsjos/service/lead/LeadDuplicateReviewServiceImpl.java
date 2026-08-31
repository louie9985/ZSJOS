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
import cn.iocoder.yudao.module.zsjos.service.advancedfilter.AdvancedFilterService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.zsjos.enums.LeadConstants.*;
import static cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants.*;

@Service
public class LeadDuplicateReviewServiceImpl implements LeadDuplicateReviewService {
    @Resource private LeadDuplicateReviewMapper reviewMapper;
    @Resource private LeadMapper leadMapper;
    @Resource private LeadSubmissionServiceImpl submissionService;
    @Resource private LeadAssignmentService assignmentService;
    @Resource private LeadAttachmentService attachmentService;
    @Resource private SecurityFrameworkService securityFrameworkService;
    @Resource private AdvancedFilterService advancedFilterService;

    @Override
    public PageResult<LeadDuplicateReviewRespVO> getPage(LeadDuplicateReviewPageReqVO request) {
        List<Long> matchedIds = advancedFilterService.matchDuplicateReviewIds(request.getKeyword(), request.getAdvancedFilter());
        PageResult<LeadDuplicateReviewDO> page = reviewMapper.selectPage(request, request.getStatus(), matchedIds);
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
        if (DUPLICATE_REVIEW_STATUS_COMPLETED.equals(review.getStatus())) {
            if (Objects.equals(review.getDecisionIdempotencyKey(), request.getIdempotencyKey())
                    && Objects.equals(review.getReviewerUserId(), reviewerUserId)) return;
            throw exception(LEAD_DUPLICATE_REVIEW_HANDLED);
        }
        LeadCreateReqVO submission = JsonUtils.parseObject(review.getSubmissionSnapshot(), LeadCreateReqVO.class);
        Map<Long, FileInfoRespDTO> files = attachmentService.validateReferences(request.getAttachments(), reviewerUserId);
        LocalDateTime now = LocalDateTime.now();
        Map<String, Object> before = new LinkedHashMap<>();
        Map<String, Object> after = new LinkedHashMap<>();
        switch (request.getResultType()) {
            case DUPLICATE_REVIEW_ACTION_ALLOW_FLOW -> {
                LeadCreateRespVO result = submissionService.createApprovedFromReview(submission,
                        review.getSubmitterUserId(), null, review.getSubmissionSourceType(), review.getSubmissionPartnerId(),
                        review.getLeadCategoryLabelSnapshot());
                after.put("personId", leadMapper.selectById(result.getLeadId()).getPersonId());
                after.put("leadId", result.getLeadId());
            }
            case DUPLICATE_REVIEW_ACTION_CLOSE_DUPLICATE -> after.put("closed", true);
            default -> throw exception(LEAD_DUPLICATE_REVIEW_RESULT_INVALID);
        }
        review.setStatus(DUPLICATE_REVIEW_STATUS_COMPLETED);
        review.setResultType(request.getResultType());
        review.setDuplicateResult(DUPLICATE_REVIEW_ACTION_ALLOW_FLOW.equals(request.getResultType())
                ? DUPLICATE_RESULT_ALLOWED : DUPLICATE_RESULT_CLOSED);
        review.setReviewOpinion(request.getOpinion().trim());
        review.setReviewAttachments(JsonUtils.toJsonString(files.values().stream().map(file -> Map.of(
                "infraFileId", file.getId(), "name", file.getName(), "type", file.getType(), "size", file.getSize())).toList()));
        review.setSelectedSalesUserId(null);
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
        if (DUPLICATE_REVIEW_STATUS_COMPLETED.equals(review.getStatus())) {
            LeadDO completedLead = review.getMatchedLeadId() == null ? null : leadMapper.selectById(review.getMatchedLeadId());
            return completedLead == null
                    ? LeadCreateRespVO.duplicateAutoClosed(null, null, null)
                    : LeadCreateRespVO.duplicateAutoClosed(completedLead.getId(), completedLead.getLeadNo(), completedLead.getStatus());
        }
        Map<String, Object> after = new LinkedHashMap<>();
        LocalDateTime now = LocalDateTime.now();
        LeadDO matchedLead = matchedLeadId == null ? null : leadMapper.selectById(matchedLeadId);
        if (matchedLead != null) {
            review.setMatchedPersonId(matchedLead.getPersonId());
            review.setMatchedLeadId(matchedLeadId);
            after.put("matchedLeadId", matchedLeadId);
        }
        after.put("autoClosed", true);
        review.setStatus(DUPLICATE_REVIEW_STATUS_COMPLETED);
        review.setResultType(DUPLICATE_REVIEW_ACTION_CLOSE_DUPLICATE);
        review.setDuplicateResult(DUPLICATE_RESULT_AUTO_CLOSED);
        review.setReviewOpinion("系统自动关闭：交叉联系方式疑似重复");
        review.setReviewAttachments("[]");
        review.setReviewerUserId(actorUserId);
        review.setReviewedAt(now);
        review.setBeforeSnapshot("{}");
        review.setAfterSnapshot(JsonUtils.toJsonString(after));
        review.setDecisionIdempotencyKey("auto-duplicate:" + review.getSubmissionIdempotencyKey());
        review.setVersion(review.getVersion() + 1);
        reviewMapper.updateById(review);
        return matchedLead == null
                ? LeadCreateRespVO.duplicateAutoClosed(null, null, null)
                : LeadCreateRespVO.duplicateAutoClosed(matchedLead.getId(), matchedLead.getLeadNo(), matchedLead.getStatus());
    }

    private LeadDuplicateReviewRespVO toResponse(LeadDuplicateReviewDO row) {
        LeadDuplicateReviewRespVO response = new LeadDuplicateReviewRespVO();
        response.setId(row.getId()); response.setStatus(row.getStatus()); response.setSubmitterUserId(row.getSubmitterUserId());
        response.setSubmissionSnapshot(row.getSubmissionSnapshot()); response.setDuplicateFlag(row.getDuplicateFlag());
        response.setDuplicateResult(row.getDuplicateResult()); response.setPrimaryRuleCode(row.getPrimaryRuleCode());
        response.setReviewFingerprint(row.getReviewFingerprint()); response.setMatchRules(row.getMatchRules());
        response.setCandidateSnapshot(row.getCandidateSnapshot()); response.setResultType(row.getResultType());
        response.setReviewOpinion(row.getReviewOpinion()); response.setSelectedSalesUserId(row.getSelectedSalesUserId());
        response.setReviewAttachments(row.getReviewAttachments()); response.setBeforeSnapshot(row.getBeforeSnapshot());
        response.setAfterSnapshot(row.getAfterSnapshot()); response.setReviewerUserId(row.getReviewerUserId());
        response.setReviewedAt(row.getReviewedAt()); response.setCreateTime(row.getCreateTime());
        return response;
    }
}
