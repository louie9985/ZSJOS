package cn.iocoder.yudao.module.zsjos.service.lead;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.framework.security.core.service.SecurityFrameworkService;
import cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.assignment.LeadAssignmentUserRespVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.duplicate.LeadDuplicateReviewDecisionReqVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.submission.LeadCreateRespVO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.LeadDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.LeadDuplicateReviewDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.lead.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static cn.iocoder.yudao.module.zsjos.enums.LeadConstants.DUPLICATE_RESULT_ALLOWED;
import static cn.iocoder.yudao.module.zsjos.enums.LeadConstants.DUPLICATE_RESULT_AUTO_CLOSED;
import static cn.iocoder.yudao.module.zsjos.enums.LeadConstants.DUPLICATE_RESULT_CLOSED;
import static cn.iocoder.yudao.module.zsjos.enums.LeadConstants.DUPLICATE_REVIEW_ACTION_ALLOW_FLOW;
import static cn.iocoder.yudao.module.zsjos.enums.LeadConstants.DUPLICATE_REVIEW_ACTION_CLOSE_DUPLICATE;
import static cn.iocoder.yudao.module.zsjos.enums.LeadNotifySceneConstants.DUPLICATE_OWNER_REMINDER;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LeadDuplicateReviewServiceImplTest {
    @InjectMocks private LeadDuplicateReviewServiceImpl service;
    @Mock private LeadDuplicateReviewMapper reviewMapper;
    @Mock private LeadMapper leadMapper;
    @Mock private LeadSubmissionServiceImpl submissionService;
    @Mock private LeadAssignmentService assignmentService;
    @Mock private SecurityFrameworkService securityFrameworkService;
    @Mock private LeadAttachmentService attachmentService;
    @Mock private LeadNotifyEventPublisher notifyEventPublisher;

    @BeforeEach
    void setUp() {
        TenantContextHolder.setTenantId(1L);
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    void crossContactAutoResolutionClosesReviewWithoutChangingLead() {
        LeadDuplicateReviewDO review = review(1L);
        LeadDO lead = lead("won", "owned", 10L);
        when(reviewMapper.selectByIdForUpdate(1L, 1L)).thenReturn(review);
        when(leadMapper.selectById(20L)).thenReturn(lead);

        LeadCreateRespVO result = service.resolveAutomatically(1L, 20L, 99L);

        assertEquals("duplicate_auto_closed", result.getOutcome());
        assertEquals("won", lead.getStatus());
        assertEquals(DUPLICATE_REVIEW_ACTION_CLOSE_DUPLICATE, review.getResultType());
        assertEquals(DUPLICATE_RESULT_AUTO_CLOSED, review.getDuplicateResult());
        verify(notifyEventPublisher, never()).publish(eq(DUPLICATE_OWNER_REMINDER), any(), any(), any(), any(), any());
        verify(reviewMapper).updateById(review);
    }

    @Test
    void allowFlowCreatesLeadFromOriginalSubmissionSnapshot() {
        LeadDuplicateReviewDO review = review(1L);
        when(reviewMapper.selectByIdForUpdate(1L, 1L)).thenReturn(review);
        when(attachmentService.validateReferences(List.of(), 99L)).thenReturn(Map.of());
        when(submissionService.createApprovedFromReview(any(), eq(5L), eq(null), eq(null), eq(null),
                eq("提交时分类"))).thenReturn(new LeadCreateRespVO(50L, "created", "unassigned", null));
        LeadDO created = new LeadDO();
        created.setId(50L);
        created.setPersonId(60L);
        when(leadMapper.selectById(50L)).thenReturn(created);

        service.decide(1L, decision(DUPLICATE_REVIEW_ACTION_ALLOW_FLOW), 99L);

        assertEquals("completed", review.getStatus());
        assertEquals(DUPLICATE_REVIEW_ACTION_ALLOW_FLOW, review.getResultType());
        assertEquals(DUPLICATE_RESULT_ALLOWED, review.getDuplicateResult());
        verify(submissionService).createApprovedFromReview(any(), eq(5L), eq(null), eq(null), eq(null),
                eq("提交时分类"));
        verify(reviewMapper).updateById(review);
    }

    @Test
    void closeDuplicateCompletesReviewWithoutCreatingLead() {
        LeadDuplicateReviewDO review = review(1L);
        when(reviewMapper.selectByIdForUpdate(1L, 1L)).thenReturn(review);
        when(attachmentService.validateReferences(List.of(), 99L)).thenReturn(Map.of());

        service.decide(1L, decision(DUPLICATE_REVIEW_ACTION_CLOSE_DUPLICATE), 99L);

        assertEquals("completed", review.getStatus());
        assertEquals(DUPLICATE_REVIEW_ACTION_CLOSE_DUPLICATE, review.getResultType());
        assertEquals(DUPLICATE_RESULT_CLOSED, review.getDuplicateResult());
        verify(submissionService, never()).createApprovedFromReview(any(), any(), any(), any(), any(), any());
        verify(reviewMapper).updateById(review);
    }

    @Test
    void centralReviewerCanChooseEveryEligibleSalesUser() {
        when(securityFrameworkService.hasPermission("zsjos:lead-duplicate-review:process")).thenReturn(true);
        when(assignmentService.getEligibleSalesUsers()).thenReturn(List.of(sales(10L), sales(20L)));

        assertEquals(List.of(10L, 20L), service.getSalesCandidates(99L).stream()
                .map(LeadAssignmentUserRespVO::getId).toList());
    }

    private static LeadDuplicateReviewDO review(Long id) {
        LeadDuplicateReviewDO review = new LeadDuplicateReviewDO();
        review.setId(id); review.setStatus("pending"); review.setSubmitterUserId(5L);
        review.setLeadCategoryLabelSnapshot("提交时分类");
        review.setSubmissionIdempotencyKey("submission-1"); review.setVersion(0);
        review.setSubmissionSnapshot("{\"name\":\"新客户\",\"mobile\":\"13800138000\","
                + "\"provinceCode\":\"OTHER\",\"cityCode\":\"OTHER\","
                + "\"sourceChannel\":\"test\",\"leadCategory\":\"test\","
                + "\"products\":[],\"attachments\":[]}");
        return review;
    }

    private static LeadDO lead(String status, String assignmentStatus, Long ownerUserId) {
        LeadDO lead = new LeadDO();
        lead.setId(20L); lead.setLeadNo("L20"); lead.setPersonId(30L);
        lead.setStatus(status); lead.setAssignmentStatus(assignmentStatus); lead.setOwnerUserId(ownerUserId);
        return lead;
    }

    private static LeadDuplicateReviewDecisionReqVO decision(String resultType) {
        LeadDuplicateReviewDecisionReqVO request = new LeadDuplicateReviewDecisionReqVO();
        request.setResultType(resultType);
        request.setOpinion("确认处理");
        request.setAttachments(List.of());
        request.setIdempotencyKey("decision-1");
        return request;
    }

    private static LeadAssignmentUserRespVO sales(Long id) {
        LeadAssignmentUserRespVO user = new LeadAssignmentUserRespVO();
        user.setId(id);
        return user;
    }
}
