package cn.iocoder.yudao.module.zsjos.service.lead;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.framework.security.core.service.SecurityFrameworkService;
import cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.assignment.LeadAssignmentUserRespVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.submission.LeadCreateRespVO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.LeadAssignmentHistoryDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.LeadDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.LeadDuplicateReviewDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.PersonDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.lead.*;
import cn.iocoder.yudao.module.zsjos.service.product.ZsjosProductSkuService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static cn.iocoder.yudao.module.zsjos.enums.LeadNotifySceneConstants.DUPLICATE_OWNER_REMINDER;
import static cn.iocoder.yudao.module.zsjos.enums.LeadNotifySceneConstants.DUPLICATE_REACTIVATED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LeadDuplicateReviewServiceImplTest {
    @InjectMocks private LeadDuplicateReviewServiceImpl service;
    @Mock private LeadDuplicateReviewMapper reviewMapper;
    @Mock private PersonMapper personMapper;
    @Mock private LeadMapper leadMapper;
    @Mock private OpportunityMapper opportunityMapper;
    @Mock private LeadIntendedProductMapper productMapper;
    @Mock private LeadAttachmentMapper attachmentMapper;
    @Mock private LeadPublicSeaRecordMapper publicSeaRecordMapper;
    @Mock private LeadAssignmentHistoryMapper assignmentHistoryMapper;
    @Mock private LeadSubmissionServiceImpl submissionService;
    @Mock private LeadAssignmentService assignmentService;
    @Mock private SecurityFrameworkService securityFrameworkService;
    @Mock private LeadAttachmentService attachmentService;
    @Mock private ZsjosProductSkuService productSkuService;
    @Mock private LeadLifecycleTaskService lifecycleTaskService;
    @Mock private LeadNotifyEventPublisher notifyEventPublisher;
    @Mock private PersonIdentityWriteService personIdentityWriteService;

    @BeforeEach
    void setUp() {
        TenantContextHolder.setTenantId(1L);
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    void activeLeadRemainsUnchangedAndNotifiesEligibleOwner() {
        LeadDuplicateReviewDO review = review(1L);
        LeadDO lead = lead("won", "owned", 10L);
        when(reviewMapper.selectByIdForUpdate(1L, 1L)).thenReturn(review);
        when(leadMapper.selectByIdForUpdate(20L, 1L)).thenReturn(lead);
        when(leadMapper.selectById(20L)).thenReturn(lead);
        when(assignmentService.getEligibleSalesUsers()).thenReturn(List.of(sales(10L)));

        LeadCreateRespVO result = service.resolveAutomatically(1L, 20L, 99L);

        assertEquals("duplicate_auto_closed", result.getOutcome());
        assertEquals("won", lead.getStatus());
        verify(notifyEventPublisher).publish(eq(DUPLICATE_OWNER_REMINDER), eq(20L), any(), eq(99L), any(), any());
        assertEquals("notify_owner", review.getResultType());
        verify(reviewMapper).updateById(review);
    }

    @Test
    void invalidLeadWithoutEligibleOwnerReactivatesIntoClaimPool() {
        LeadDuplicateReviewDO review = review(1L);
        LeadDO lead = lead("invalid", "owned", 10L);
        PersonDO person = new PersonDO();
        person.setId(30L); person.setName("历史客户");
        when(reviewMapper.selectByIdForUpdate(1L, 1L)).thenReturn(review);
        when(leadMapper.selectByIdForUpdate(20L, 1L)).thenReturn(lead);
        when(leadMapper.selectById(20L)).thenReturn(lead);
        when(personMapper.selectById(30L)).thenReturn(person);
        when(productMapper.selectListByLeadId(20L)).thenReturn(List.of());
        when(attachmentMapper.selectListByLeadId(20L)).thenReturn(List.of());
        when(assignmentService.getEligibleSalesUsers()).thenReturn(List.of());
        doAnswer(invocation -> {
            invocation.getArgument(0, LeadAssignmentHistoryDO.class).setId(40L);
            return 1;
        }).when(assignmentHistoryMapper).insert(any(LeadAssignmentHistoryDO.class));

        LeadCreateRespVO result = service.resolveAutomatically(1L, 20L, 99L);

        assertEquals("activated", result.getOutcome());
        assertEquals("submitted", lead.getStatus());
        assertEquals("public_pool", lead.getAssignmentStatus());
        assertNull(lead.getOwnerUserId());
        assertNull(lead.getOwnershipStartedAt());
        verify(lifecycleTaskService).cancelFirstFollowUpTasks(eq(20L), any(), any());
        verify(lifecycleTaskService).cancelFollowUpReminders(eq(20L), any(), any());
        verify(notifyEventPublisher).publish(eq(DUPLICATE_REACTIVATED), eq(20L), any(), eq(99L), any(), any());
    }

    @Test
    void pendingAcceptanceLeadClosesWithoutDuplicateReminder() {
        LeadDuplicateReviewDO review = review(1L);
        LeadDO lead = lead("submitted", "pending_acceptance", null);
        lead.setPendingAssigneeUserId(10L);
        when(reviewMapper.selectByIdForUpdate(1L, 1L)).thenReturn(review);
        when(leadMapper.selectByIdForUpdate(20L, 1L)).thenReturn(lead);
        when(leadMapper.selectById(20L)).thenReturn(lead);

        LeadCreateRespVO result = service.resolveAutomatically(1L, 20L, 99L);

        assertEquals("duplicate_auto_closed", result.getOutcome());
        assertEquals("notify_owner", review.getResultType());
        verify(notifyEventPublisher, never()).publish(eq(DUPLICATE_OWNER_REMINDER), any(), any(), any(), any(), any());
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

    private static LeadAssignmentUserRespVO sales(Long id) {
        LeadAssignmentUserRespVO user = new LeadAssignmentUserRespVO();
        user.setId(id);
        return user;
    }
}
