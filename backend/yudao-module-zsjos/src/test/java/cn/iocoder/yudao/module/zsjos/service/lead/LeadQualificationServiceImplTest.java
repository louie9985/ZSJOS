package cn.iocoder.yudao.module.zsjos.service.lead;

import cn.iocoder.yudao.framework.common.biz.system.dict.dto.DictDataRespDTO;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.system.api.dict.DictDataApi;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.qualification.LeadDispositionReqVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.qualification.LeadJudgeInvalidReqVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.qualification.LeadJudgeValidReqVO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.event.BusinessEventDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.LeadDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.OpportunityDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.event.BusinessEventMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.lead.LeadAssignmentHistoryMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.lead.LeadMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.lead.LeadIntendedProductMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.lead.OpportunityMapper;
import cn.iocoder.yudao.module.zsjos.service.task.BusinessTaskReminderService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static cn.iocoder.yudao.module.zsjos.enums.LeadNotifySceneConstants.QUALIFICATION_RESTORED;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LeadQualificationServiceImplTest {
    @InjectMocks private LeadQualificationServiceImpl service;
    @Mock private LeadMapper leadMapper;
    @Mock private LeadAssignmentHistoryMapper historyMapper;
    @Mock private BusinessEventMapper eventMapper;
    @Mock private DictDataApi dictDataApi;
    @Mock private AdminUserApi adminUserApi;
    @Mock private LeadAssignmentService assignmentService;
    @Mock private LeadObjectPermissionService permissionService;
    @Mock private LeadLifecycleTaskService lifecycleTaskService;
    @Mock private BusinessTaskReminderService taskReminderService;
    @Mock private LeadNotifyEventPublisher notifyEventPublisher;
    @Mock private OpportunityMapper opportunityMapper;
    @Mock private LeadIntendedProductMapper intendedProductMapper;

    @Test
    void judgeValidCompletesCurrentQualificationRound() {
        LeadDO lead = pendingLead();
        when(leadMapper.selectByIdForUpdate(1L, 9L)).thenReturn(lead);
        when(eventMapper.selectByIdempotencyKey("lead-qualification:request-1")).thenReturn(null);
        doAnswer(invocation -> {
            invocation.<OpportunityDO>getArgument(0).setId(30L);
            return 1;
        }).when(opportunityMapper).insert(any(OpportunityDO.class));

        withTenant(() -> service.judgeValid(1L, 20L, command("request-1")));

        assertEquals("converted", lead.getStatus());
        assertEquals("closed", lead.getAssignmentStatus());
        assertEquals("已确认有明确学习意向", lead.getValidDescription());
        assertEquals(20L, lead.getQualifiedByUserId());
        assertNotNull(lead.getQualifiedAt());
        verify(opportunityMapper).insert(argThat((OpportunityDO opportunity) ->
                "initial_conversion".equals(opportunity.getType())
                        && "open".equals(opportunity.getStatus())
                        && opportunity.getLeadId().equals(1L)));
        verify(lifecycleTaskService).completeQualificationTask(eq(1L), eq(2), any(LocalDateTime.class));
        verify(eventMapper).insert(argThat((BusinessEventDO event) ->
                "lead_qualified_valid".equals(event.getEventType())));
    }

    @Test
    void judgeInvalidStoresStableReasonAndLabelSnapshot() {
        LeadDO lead = pendingLead();
        when(leadMapper.selectByIdForUpdate(1L, 9L)).thenReturn(lead);
        when(eventMapper.selectByIdempotencyKey("lead-qualification:request-2")).thenReturn(null);
        DictDataRespDTO reason = new DictDataRespDTO();
        reason.setValue("no_budget"); reason.setLabel("暂无预算"); reason.setStatus(0);
        when(dictDataApi.getDictDataList("zsjos_lead_invalid_reason")).thenReturn(List.of(reason));
        LeadJudgeInvalidReqVO request = new LeadJudgeInvalidReqVO();
        request.setIdempotencyKey("request-2"); request.setReasonCode("no_budget");
        request.setDescription("  客户确认本季度无预算  ");

        withTenant(() -> service.judgeInvalid(1L, 20L, request));

        assertEquals("invalid", lead.getStatus());
        assertEquals("no_budget", lead.getInvalidReason());
        assertEquals("暂无预算", lead.getInvalidReasonLabelSnapshot());
        assertEquals("客户确认本季度无预算", lead.getInvalidDescription());
    }

    @Test
    void judgeInvalidAfterConversionClosesOpportunity() {
        LeadDO lead = pendingLead();
        lead.setStatus("converted"); lead.setAssignmentStatus("closed");
        OpportunityDO opportunity = new OpportunityDO();
        opportunity.setId(30L); opportunity.setStatus("following");
        when(leadMapper.selectByIdForUpdate(1L, 9L)).thenReturn(lead);
        when(eventMapper.selectByIdempotencyKey("lead-qualification:request-lost")).thenReturn(null);
        when(opportunityMapper.selectByLeadId(1L)).thenReturn(opportunity);
        DictDataRespDTO reason = new DictDataRespDTO();
        reason.setValue("no_budget"); reason.setLabel("暂无预算"); reason.setStatus(0);
        when(dictDataApi.getDictDataList("zsjos_lead_invalid_reason")).thenReturn(List.of(reason));
        LeadJudgeInvalidReqVO request = new LeadJudgeInvalidReqVO();
        request.setIdempotencyKey("request-lost"); request.setReasonCode("no_budget");
        request.setDescription("客户终止计划");

        withTenant(() -> service.judgeInvalid(1L, 20L, request));

        assertEquals("invalid", lead.getStatus());
        assertEquals("lost", opportunity.getStatus());
        assertNotNull(opportunity.getLostAt());
        verify(opportunityMapper).updateById(opportunity);
        verify(lifecycleTaskService, never()).completeQualificationTask(anyLong(), anyInt(), any());
    }

    @Test
    void schedulerSuspendsOnlyAfterItLocksStillExpiredLead() {
        LeadDO lead = pendingLead();
        lead.setQualificationDeadlineAt(LocalDateTime.now().minusMinutes(1));
        when(leadMapper.selectExpiredQualifications(any(LocalDateTime.class))).thenReturn(List.of(lead));
        when(leadMapper.selectByIdForUpdate(1L, 9L)).thenReturn(lead);
        when(lifecycleTaskService.getQualificationTaskId(1L, 2)).thenReturn(88L);

        int processed = withTenantResult(service::processExpired);

        assertEquals(1, processed);
        assertEquals("suspended", lead.getStatus());
        assertNotNull(lead.getSuspendedAt());
        InOrder timeoutOrder = inOrder(taskReminderService, lifecycleTaskService);
        timeoutOrder.verify(taskReminderService).emitDueForTask(eq(88L), any(LocalDateTime.class));
        timeoutOrder.verify(lifecycleTaskService).cancelQualificationTask(eq(1L), eq(2),
                any(LocalDateTime.class), anyString());
        verifyNoInteractions(notifyEventPublisher);
    }

    @Test
    void restoreStartsNewQualificationRoundAndPublishesDisposition() {
        LeadDO lead = pendingLead();
        lead.setStatus("suspended");
        when(leadMapper.selectByIdForUpdate(1L, 9L)).thenReturn(lead);
        when(eventMapper.selectByIdempotencyKey("lead-disposition:request-3")).thenReturn(null);
        when(permissionService.hasQualificationManageAll()).thenReturn(true);
        when(assignmentService.getEligibleSalesUsers()).thenReturn(List.of(candidate(20L)));
        LeadDispositionReqVO request = new LeadDispositionReqVO();
        request.setIdempotencyKey("request-3"); request.setReason("主管恢复");

        withTenant(() -> service.restore(1L, 99L, request));

        assertEquals("submitted", lead.getStatus());
        verify(lifecycleTaskService).createQualificationTask(eq(lead), eq(20L), any(LocalDateTime.class));
        verify(notifyEventPublisher).publish(eq(QUALIFICATION_RESTORED), eq(1L), anyString(),
                eq(99L), any(LocalDateTime.class), anyMap());
    }

    @Test
    void repeatedJudgmentReturnsWithoutSecondMutation() {
        LeadDO lead = pendingLead();
        BusinessEventDO event = new BusinessEventDO();
        event.setAggregateId(1L); event.setOperatorUserId(20L); event.setEventType("lead_qualified_valid");
        when(leadMapper.selectByIdForUpdate(1L, 9L)).thenReturn(lead);
        when(eventMapper.selectByIdempotencyKey("lead-qualification:request-4")).thenReturn(event);

        withTenant(() -> service.judgeValid(1L, 20L, command("request-4")));

        verify(leadMapper, never()).updateById(any(LeadDO.class));
        verify(lifecycleTaskService, never()).completeQualificationTask(anyLong(), anyInt(), any());
    }

    @Test
    void suspendedTransferCandidatesExcludeCurrentOwner() {
        LeadDO lead = pendingLead();
        lead.setStatus("suspended");
        when(leadMapper.selectById(1L)).thenReturn(lead);
        when(permissionService.hasQualificationManageAll()).thenReturn(true);
        when(assignmentService.getEligibleSalesUsers()).thenReturn(List.of(candidate(20L), candidate(30L)));

        var result = service.getTransferCandidates(1L, 99L);

        assertEquals(List.of(30L), result.stream().map(item -> item.getId()).toList());
    }

    private LeadDO pendingLead() {
        LeadDO lead = new LeadDO();
        lead.setId(1L); lead.setStatus("submitted"); lead.setAssignmentStatus("owned");
        lead.setOwnerUserId(20L); lead.setQualificationRoundNo(2);
        lead.setQualificationDeadlineAt(LocalDateTime.now().plusHours(1));
        return lead;
    }

    private LeadJudgeValidReqVO command(String key) {
        LeadJudgeValidReqVO request = new LeadJudgeValidReqVO();
        request.setIdempotencyKey(key);
        request.setRemark("已确认有明确学习意向");
        return request;
    }

    private cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.assignment.LeadAssignmentUserRespVO candidate(Long id) {
        cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.assignment.LeadAssignmentUserRespVO result =
                new cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.assignment.LeadAssignmentUserRespVO();
        result.setId(id);
        return result;
    }

    private void withTenant(Runnable runnable) {
        try (MockedStatic<TenantContextHolder> tenant = mockStatic(TenantContextHolder.class)) {
            tenant.when(TenantContextHolder::getRequiredTenantId).thenReturn(9L);
            runnable.run();
        }
    }

    private int withTenantResult(java.util.function.IntSupplier supplier) {
        try (MockedStatic<TenantContextHolder> tenant = mockStatic(TenantContextHolder.class)) {
            tenant.when(TenantContextHolder::getRequiredTenantId).thenReturn(9L);
            return supplier.getAsInt();
        }
    }
}
