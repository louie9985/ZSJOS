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
import cn.iocoder.yudao.module.zsjos.service.advancedfilter.AdvancedFilterService;
import cn.iocoder.yudao.module.zsjos.service.cashback.CashbackService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
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
    @org.junit.jupiter.api.BeforeAll
    static void initializeMybatisLambdaMetadata() {
        var assistant = new org.apache.ibatis.builder.MapperBuilderAssistant(
                new com.baomidou.mybatisplus.core.MybatisConfiguration(), "");
        com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(assistant, LeadDO.class);
        com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(assistant, OpportunityDO.class);
    }
    @InjectMocks private LeadQualificationServiceImpl service;
    @Mock private cn.iocoder.yudao.module.zsjos.service.lead.flow.LeadFlowRecorder flowRecorder;
    @Mock private LeadMapper leadMapper;
    @Mock private cn.iocoder.yudao.module.zsjos.dal.mysql.order.SalesOrderMapper salesOrderMapper;
    @Mock private cn.iocoder.yudao.module.zsjos.dal.mysql.lead.LeadTransferRequestMapper leadTransferRequestMapper;
    @Mock private LeadPublicSeaPoolService publicSeaPoolService;
    @Mock private cn.iocoder.yudao.module.zsjos.dal.mysql.lead.LeadPublicSeaPoolCompatibilityRecordMapper publicSeaPoolCompatibilityRecordMapper;
    @Mock private cn.iocoder.yudao.module.zsjos.dal.mysql.lead.LeadAppealMapper appealMapper;
    @Mock private LeadRestoreOwnerPolicy restoreOwnerPolicy;
    @Mock private LeadTeamTransferPolicy teamTransferPolicy;
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
    @Mock private AdvancedFilterService advancedFilterService;
    @Mock private CashbackService cashbackService;
    @Mock private LeadCategorySnapshotService categorySnapshotService;

    @org.junit.jupiter.api.BeforeEach
    void setUpAdvancedFilter() {
        lenient().when(advancedFilterService.matchLeadIds(any())).thenReturn(null);
        lenient().when(categorySnapshotService.requireEnabled(any()))
                .thenReturn(new LeadCategorySnapshotService.Selection(null, null));
    }

    @Test
    void restoreRejectsIneligibleOwnerBeforeAnyMutation() {
        LeadDO lead = pendingLead(); lead.setStatus("suspended"); lead.setOwnerIdentity("education");
        when(leadMapper.selectByIdForUpdate(1L, 9L)).thenReturn(lead);
        LeadDispositionReqVO request = new LeadDispositionReqVO();
        request.setIdempotencyKey("disabled-owner"); request.setReason("恢复");
        withTenant(() -> {
            var error = assertThrows(cn.iocoder.yudao.framework.common.exception.ServiceException.class,
                    () -> service.restore(1L, 99L, request));
            assertEquals(cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants.LEAD_QUALIFICATION_RESTORE_OWNER_INVALID.getCode(), error.getCode());
        });
        assertEquals("suspended", lead.getStatus());
        verifyNoInteractions(lifecycleTaskService, notifyEventPublisher);
        verify(leadMapper, never()).updateById(any(LeadDO.class));
    }

    @Test
    void judgeValidCompletesCurrentQualificationRound() {
        LeadDO lead = pendingLead();
        lead.setSuspendedAt(LocalDateTime.now().minusHours(1));
        lead.setLeadCategory("high_intent");
        lead.setLeadCategoryLabelSnapshot("提交时分类");
        when(categorySnapshotService.requireEnabled(any()))
                .thenReturn(new LeadCategorySnapshotService.Selection("high_intent", "当前字典名称"));
        when(leadMapper.selectByIdForUpdate(1L, 9L)).thenReturn(lead);
        when(eventMapper.selectByIdempotencyKey("lead-qualification:request-1")).thenReturn(null);
        doAnswer(invocation -> {
            invocation.<OpportunityDO>getArgument(0).setId(30L);
            return 1;
        }).when(opportunityMapper).insert(any(OpportunityDO.class));

        withTenant(() -> service.judgeValid(1L, 20L, command("request-1")));

        assertEquals("valid", lead.getStatus());
        assertNull(lead.getSuspendedAt());
        verify(leadMapper).updateClearingSuspension(lead);
        assertEquals("owned", lead.getAssignmentStatus());
        assertEquals("已确认有明确学习意向", lead.getValidDescription());
        assertEquals(20L, lead.getQualifiedByUserId());
        assertEquals("提交时分类", lead.getLeadCategoryLabelSnapshot());
        assertNotNull(lead.getQualifiedAt());
        verify(opportunityMapper).insert(argThat((OpportunityDO opportunity) ->
                "initial_conversion".equals(opportunity.getType())
                        && "open".equals(opportunity.getStatus())
                        && opportunity.getLeadId().equals(1L)));
        verify(lifecycleTaskService).completeQualificationTask(eq(1L), eq(2), any(LocalDateTime.class));
        verify(cashbackService).ensureValidCashback(1L);
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
        verify(lifecycleTaskService).cancelFirstFollowUpTasks(eq(1L), any(LocalDateTime.class), eq("客资判定无效"));
        verify(lifecycleTaskService).cancelFollowUpReminders(eq(1L), any(LocalDateTime.class), eq("客资判定无效"));
        verify(lifecycleTaskService).cancelQualificationTask(eq(1L), eq(2),
                any(LocalDateTime.class), eq("客资判定无效"));
    }

    @Test
    void judgeInvalidAfterValidQualificationClosesOpportunity() {
        LeadDO lead = pendingLead();
        lead.setStatus("valid"); lead.setAssignmentStatus("owned");
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
    void judgeValidRestoresExistingOpportunityInsteadOfCreatingAnother() {
        LeadDO lead = pendingLead();
        OpportunityDO opportunity = new OpportunityDO();
        opportunity.setId(30L); opportunity.setStatus("lost"); opportunity.setLostAt(LocalDateTime.now());
        opportunity.setLostReason("历史关闭"); opportunity.setOwnerUserId(99L);
        when(leadMapper.selectByIdForUpdate(1L, 9L)).thenReturn(lead);
        when(eventMapper.selectByIdempotencyKey("lead-qualification:request-restore")).thenReturn(null);
        when(opportunityMapper.selectByLeadId(1L)).thenReturn(opportunity);

        withTenant(() -> service.judgeValid(1L, 20L, command("request-restore")));

        assertEquals("open", opportunity.getStatus());
        assertEquals(20L, opportunity.getOwnerUserId());
        assertNull(opportunity.getLostAt());
        assertNull(opportunity.getLostReason());
        verify(opportunityMapper).updateById(opportunity);
        verify(opportunityMapper, never()).insert(any(OpportunityDO.class));
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
        timeoutOrder.verify(lifecycleTaskService).cancelFirstFollowUpTasks(eq(1L), any(LocalDateTime.class),
                eq("有效性判定超时自动挂起"));
        timeoutOrder.verify(lifecycleTaskService).cancelFollowUpReminders(eq(1L), any(LocalDateTime.class),
                eq("有效性判定超时自动挂起"));
        verifyNoInteractions(notifyEventPublisher);
    }

    @Test
    void restoreStartsNewQualificationRoundAndPublishesDisposition() {
        LeadDO lead = pendingLead();
        lead.setStatus("suspended");
        lead.setSuspendedAt(LocalDateTime.now().minusHours(1));
        when(leadMapper.selectByIdForUpdate(1L, 9L)).thenReturn(lead);
        when(eventMapper.selectByIdempotencyKey("lead-disposition:request-3")).thenReturn(null);
        when(restoreOwnerPolicy.isEligible(lead)).thenReturn(true);
        LeadDispositionReqVO request = new LeadDispositionReqVO();
        request.setIdempotencyKey("request-3"); request.setReason("主管恢复");

        withTenant(() -> service.restore(1L, 99L, request));

        assertEquals("submitted", lead.getStatus());
        assertNull(lead.getSuspendedAt());
        verify(leadMapper).updateClearingSuspension(lead);
        verify(lifecycleTaskService).createQualificationTask(eq(lead), eq(20L), any(LocalDateTime.class));
        verify(notifyEventPublisher).publish(eq(QUALIFICATION_RESTORED), eq(1L), anyString(),
                eq(99L), any(LocalDateTime.class), anyMap());
    }

    @ParameterizedTest
    @CsvSource({"education_self_sourced,self", "partner,specified"})
    void restoreEducationKeepsOriginalOwnerAndIdentity(String source, String dispatchMode) {
        LeadDO lead = pendingLead();
        lead.setStatus("suspended");
        lead.setOwnerIdentity("education");
        lead.setSourceType(source);
        lead.setDispatchMode(dispatchMode);
        lead.setSuspendedAt(LocalDateTime.now().minusHours(1));
        when(leadMapper.selectByIdForUpdate(1L, 9L)).thenReturn(lead);
        when(eventMapper.selectByIdempotencyKey("lead-disposition:request-3")).thenReturn(null);
        LeadRestoreOwnerPolicy policy = new LeadRestoreOwnerPolicy();
        org.springframework.test.util.ReflectionTestUtils.setField(policy, "teamTransferPolicy", new LeadTeamTransferPolicy());
        LeadSubmissionIdentityService identityService = mock(LeadSubmissionIdentityService.class);
        when(identityService.isEligibleEducationOwner(20L)).thenReturn(true);
        org.springframework.test.util.ReflectionTestUtils.setField(policy, "identityService", identityService);
        org.springframework.test.util.ReflectionTestUtils.setField(service, "restoreOwnerPolicy", policy);
        LeadDispositionReqVO request = new LeadDispositionReqVO();
        request.setIdempotencyKey("request-3"); request.setReason("主管恢复");

        withTenant(() -> service.restore(1L, 99L, request));

        assertEquals("submitted", lead.getStatus());
        assertNull(lead.getSuspendedAt());
        verify(leadMapper).updateClearingSuspension(lead);
        assertEquals(20L, lead.getOwnerUserId());
        assertEquals("education", lead.getOwnerIdentity());
        assertEquals(source, lead.getSourceType());
        assertEquals(dispatchMode, lead.getDispatchMode());
        verify(lifecycleTaskService).createQualificationTask(eq(lead), eq(20L), any(LocalDateTime.class));
        verify(notifyEventPublisher).publish(eq(QUALIFICATION_RESTORED), eq(1L), anyString(),
                eq(99L), any(LocalDateTime.class), anyMap());
        ArgumentCaptor<BusinessEventDO> event = ArgumentCaptor.forClass(BusinessEventDO.class);
        verify(eventMapper).insert(event.capture());
        assertEquals("主管恢复", event.getValue().getReason());
        when(eventMapper.selectByIdempotencyKey("lead-disposition:request-3")).thenReturn(event.getValue());
        withTenant(() -> service.restore(1L, 99L, request));
        verify(lifecycleTaskService, times(1)).createQualificationTask(eq(lead), eq(20L), any(LocalDateTime.class));
        verify(eventMapper, times(1)).insert(any(BusinessEventDO.class));
        verify(leadMapper, times(1)).updateClearingSuspension(lead);
        verifyNoInteractions(assignmentService);
    }

    @Test
    void transferClearsStaleSuspensionBeforeRequalification() {
        LeadDO lead = pendingLead();
        lead.setStatus("suspended");
        lead.setSuspendedAt(LocalDateTime.now().minusHours(1));
        when(leadMapper.selectByIdForUpdate(1L, 9L)).thenReturn(lead);
        when(eventMapper.selectByIdempotencyKey("lead-disposition:request-transfer")).thenReturn(null);
        when(permissionService.hasQualificationManageAll()).thenReturn(true);
        when(assignmentService.getEligibleSalesUsers()).thenReturn(List.of(candidate(30L)));
        doAnswer(invocation -> {
            invocation.<cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.LeadAssignmentHistoryDO>getArgument(0).setId(77L);
            return 1;
        }).when(historyMapper).insert(any(cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.LeadAssignmentHistoryDO.class));
        var transfer = new cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.qualification.LeadTransferReqVO();
        transfer.setIdempotencyKey("request-transfer"); transfer.setReason("主管转派"); transfer.setSalesUserId(30L);

        withTenant(() -> service.transfer(1L, 99L, transfer));

        assertEquals("submitted", lead.getStatus());
        assertNull(lead.getSuspendedAt());
        verify(leadMapper).updateClearingSuspension(lead);
        assertEquals("owned", lead.getAssignmentStatus());
    }

    @Test
    void salesRecycleClearsCancelledSubmittedQualificationDeadline() {
        LeadDO lead = pendingLead(); lead.setStatus("submitted"); lead.setOwnerIdentity("sales");
        lead.setQualificationStartedAt(LocalDateTime.now().minusHours(1));
        lead.setQualificationDeadlineAt(LocalDateTime.now().plusHours(1));
        when(leadMapper.selectByIdForUpdate(1L, 9L)).thenReturn(lead);
        doAnswer(invocation -> {
            invocation.<cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.LeadAssignmentHistoryDO>getArgument(0).setId(77L);
            return 1;
        }).when(historyMapper).insert(any(cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.LeadAssignmentHistoryDO.class));
        withTenant(() -> service.salesSupervisorRecycle(1L, 99L, disposition("clear-qualification")));
        assertNull(lead.getQualificationStartedAt());
        assertNull(lead.getQualificationDeadlineAt());
    }

    @Test
    void salesRecycleClearsCurrentOpportunityReminderButKeepsSuspensionFacts() {
        LeadDO lead = pendingLead(); lead.setStatus("suspended"); lead.setOwnerIdentity("sales");
        LocalDateTime deadline = LocalDateTime.now().minusHours(1); lead.setQualificationDeadlineAt(deadline);
        OpportunityDO opportunity = new OpportunityDO(); opportunity.setId(42L); opportunity.setNextFollowUpAt(LocalDateTime.now().plusDays(1));
        when(leadMapper.selectByIdForUpdate(1L, 9L)).thenReturn(lead);
        when(opportunityMapper.selectByLeadId(1L)).thenReturn(opportunity);
        doAnswer(invocation -> {
            invocation.<cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.LeadAssignmentHistoryDO>getArgument(0).setId(77L);
            return 1;
        }).when(historyMapper).insert(any(cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.LeadAssignmentHistoryDO.class));
        withTenant(() -> service.salesSupervisorRecycle(1L, 99L, disposition("clear-reminder")));
        assertEquals(deadline, lead.getQualificationDeadlineAt());
        verify(opportunityMapper).update(isNull(), any(com.baomidou.mybatisplus.core.conditions.Wrapper.class));
    }

    @Test
    void legacyQualificationTransferPreservesSalesRecycledValidStatus() {
        LeadDO lead = pendingLead(); lead.setStatus("valid"); lead.setAssignmentStatus("recycle_pending");
        lead.setOwnerUserId(null); lead.setRecycleSourceOwnerUserId(20L);
        when(leadMapper.selectByIdForUpdate(1L, 9L)).thenReturn(lead);
        when(permissionService.hasQualificationManageAll()).thenReturn(true);
        when(assignmentService.getEligibleSalesUsers()).thenReturn(List.of(candidate(30L)));
        doAnswer(invocation -> {
            invocation.<cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.LeadAssignmentHistoryDO>getArgument(0).setId(77L);
            return 1;
        }).when(historyMapper).insert(any(cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.LeadAssignmentHistoryDO.class));
        var transfer = new cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.qualification.LeadTransferReqVO();
        transfer.setIdempotencyKey("legacy-sales-transfer"); transfer.setReason("主管转派"); transfer.setSalesUserId(30L);
        withTenant(() -> service.transfer(1L, 99L, transfer));
        assertEquals("valid", lead.getStatus());
        verify(lifecycleTaskService, never()).createQualificationTask(any(), anyLong(), any());
    }

    @Test
    void releaseCannotResetRecycledSuspendedStatus() {
        LeadDO lead = pendingLead(); lead.setStatus("suspended"); lead.setAssignmentStatus("recycle_pending");
        lead.setOwnerUserId(null); lead.setRecycleSourceOwnerUserId(20L);
        when(leadMapper.selectByIdForUpdate(1L, 9L)).thenReturn(lead);
        withTenant(() -> assertThrows(cn.iocoder.yudao.framework.common.exception.ServiceException.class,
                () -> service.releaseToClaimPool(1L, 99L, disposition("release-suspended"))));
        assertEquals("suspended", lead.getStatus());
        verifyNoInteractions(lifecycleTaskService);
    }

    @Test
    void legacyQualificationRecycleSalesPreservesSuspensionAndChecksApproval() {
        LeadDO lead = pendingLead(); lead.setStatus("suspended"); lead.setOwnerIdentity("sales");
        when(leadMapper.selectByIdForUpdate(1L, 9L)).thenReturn(lead);
        when(salesOrderMapper.selectOne(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class)))
                .thenReturn(new cn.iocoder.yudao.module.zsjos.dal.dataobject.order.SalesOrderDO());
        withTenant(() -> {
            var error = assertThrows(cn.iocoder.yudao.framework.common.exception.ServiceException.class,
                    () -> service.recycle(1L, 99L, disposition("legacy-sales-recycle")));
            assertEquals(cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants.LEAD_RECYCLE_APPROVAL_ACTIVE.getCode(), error.getCode());
        });
        assertEquals("suspended", lead.getStatus());
        assertEquals(20L, lead.getOwnerUserId());
        verifyNoInteractions(publicSeaPoolService, lifecycleTaskService);
    }

    @Test
    void salesRecycleClosesOnlyLockedCompatibilityRecord() {
        LeadDO lead = pendingLead(); lead.setStatus("valid"); lead.setOwnerIdentity("sales");
        var compatibility = new cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.LeadPublicSeaPoolCompatibilityRecordDO();
        compatibility.setId(44L); compatibility.setLeadId(1L);
        when(leadMapper.selectByIdForUpdate(1L, 9L)).thenReturn(lead);
        when(publicSeaPoolCompatibilityRecordMapper.selectByLeadIdForUpdate(1L, 9L)).thenReturn(compatibility);
        doAnswer(invocation -> {
            invocation.<cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.LeadAssignmentHistoryDO>getArgument(0).setId(77L);
            return 1;
        }).when(historyMapper).insert(any(cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.LeadAssignmentHistoryDO.class));
        withTenant(() -> service.salesSupervisorRecycle(1L, 99L, disposition("compatibility-recycle")));
        verify(publicSeaPoolService).terminateForOwnerTransfer(eq(1L), isNull(), eq(99L), any(), anyString());
        verify(publicSeaPoolCompatibilityRecordMapper).deleteById(44L);
        verify(publicSeaPoolCompatibilityRecordMapper, never()).deleteByLeadId(anyLong());
    }

    @ParameterizedTest
    @CsvSource({"order", "transfer", "appeal"})
    void salesRecycleRejectsEachActiveApproval(String type) {
        LeadDO lead = pendingLead(); lead.setStatus("valid"); lead.setOwnerIdentity("sales");
        when(leadMapper.selectByIdForUpdate(1L, 9L)).thenReturn(lead);
        if ("order".equals(type)) when(salesOrderMapper.selectOne(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class)))
                .thenReturn(new cn.iocoder.yudao.module.zsjos.dal.dataobject.order.SalesOrderDO());
        if ("transfer".equals(type)) when(leadTransferRequestMapper.selectOne(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class)))
                .thenReturn(new cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.LeadTransferRequestDO());
        if ("appeal".equals(type)) when(appealMapper.selectOne(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class)))
                .thenReturn(new cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.LeadAppealDO());
        withTenant(() -> {
            var error = assertThrows(cn.iocoder.yudao.framework.common.exception.ServiceException.class,
                    () -> service.salesSupervisorRecycle(1L, 99L, disposition("approval")));
            assertEquals(cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants.LEAD_RECYCLE_APPROVAL_ACTIVE.getCode(), error.getCode());
        });
        assertEquals(20L, lead.getOwnerUserId());
        verifyNoInteractions(publicSeaPoolService, lifecycleTaskService);
        verify(historyMapper, never()).insert(any(cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.LeadAssignmentHistoryDO.class));
    }

    @ParameterizedTest
    @CsvSource({"submitted", "suspended", "valid", "converted", "invalid", "won", "closed"})
    void salesSupervisorRecyclePreservesBusinessStatus(String status) {
        LeadDO lead = pendingLead(); lead.setStatus(status); lead.setOwnerIdentity("sales");
        LocalDateTime suspendedAt = LocalDateTime.now().minusDays(1);
        lead.setSuspendedAt(suspendedAt);
        if ("closed".equals(status)) lead.setClosedAt(suspendedAt);
        when(leadMapper.selectByIdForUpdate(1L, 9L)).thenReturn(lead);
        doAnswer(invocation -> {
            invocation.<cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.LeadAssignmentHistoryDO>getArgument(0).setId(77L);
            return 1;
        }).when(historyMapper).insert(any(cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.LeadAssignmentHistoryDO.class));
        withTenant(() -> service.salesSupervisorRecycle(1L, 99L, disposition("sales-recycle")));
        assertEquals(status, lead.getStatus());
        assertEquals(suspendedAt, lead.getSuspendedAt());
        assertEquals("recycle_pending", lead.getAssignmentStatus());
        assertEquals(20L, lead.getRecycleSourceOwnerUserId());
        assertNull(lead.getOwnerUserId());
        verify(lifecycleTaskService).cancelFirstFollowUpTasks(eq(1L), any(), anyString());
    }

    @ParameterizedTest
    @CsvSource({"submitted,true,true", "suspended,false,false", "valid,true,false", "converted,true,false", "invalid,false,false", "won,false,false", "closed,false,false"})
    void salesRecycledTransferPreservesStatusAndSchedulesApplicableTasks(String status, boolean first, boolean qualification) {
        LeadDO lead = pendingLead(); lead.setStatus(status); lead.setAssignmentStatus("recycle_pending");
        lead.setOwnerUserId(null); lead.setRecycleSourceOwnerUserId(20L);
        when(leadMapper.selectByIdForUpdate(1L, 9L)).thenReturn(lead);
        when(permissionService.hasQualificationManageAll()).thenReturn(true);
        when(assignmentService.getEligibleSalesUsers()).thenReturn(List.of(candidate(30L)));
        doAnswer(invocation -> {
            invocation.<cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.LeadAssignmentHistoryDO>getArgument(0).setId(77L);
            return 1;
        }).when(historyMapper).insert(any(cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.LeadAssignmentHistoryDO.class));
        var transfer = new cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.qualification.LeadTransferReqVO();
        transfer.setIdempotencyKey("sales-transfer"); transfer.setReason("主管转派"); transfer.setSalesUserId(30L);
        withTenant(() -> service.salesSupervisorTransferRecycled(1L, 99L, transfer));
        assertEquals(status, lead.getStatus());
        assertEquals("owned", lead.getAssignmentStatus());
        assertEquals(30L, lead.getOwnerUserId());
        verify(lifecycleTaskService, times(first ? 1 : 0)).createFirstFollowUpTask(eq(1L), eq(30L), eq(77L), any(), anyString(), anyString());
        verify(lifecycleTaskService, times(qualification ? 1 : 0)).createQualificationTask(eq(lead), eq(30L), any());
    }

    @Test
    void dispositionTransitionsAdvanceLeadActivityTime() {
        LeadDO suspended = pendingLead();
        suspended.setId(11L); suspended.setStatus("suspended");
        LeadDO recycled = pendingLead();
        recycled.setId(12L); recycled.setAssignmentStatus("recycle_pending");
        recycled.setOwnerUserId(null); recycled.setRecycleSourceOwnerUserId(20L);
        LeadDO ownedForRecycle = pendingLead();
        ownedForRecycle.setId(13L);
        LeadDO ownedForRelease = pendingLead();
        ownedForRelease.setId(14L);
        when(leadMapper.selectByIdForUpdate(11L, 9L)).thenReturn(suspended);
        when(teamTransferPolicy.isEducation(suspended)).thenReturn(true);
        when(leadMapper.selectByIdForUpdate(12L, 9L)).thenReturn(recycled);
        when(leadMapper.selectByIdForUpdate(13L, 9L)).thenReturn(ownedForRecycle);
        when(leadMapper.selectByIdForUpdate(14L, 9L)).thenReturn(ownedForRelease);
        doAnswer(invocation -> {
            invocation.<cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.LeadAssignmentHistoryDO>
                    getArgument(0).setId(77L);
            return 1;
        }).when(historyMapper).insert(any(
                cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.LeadAssignmentHistoryDO.class));

        withTenant(() -> service.recycle(11L, 99L, disposition("recycle")));
        withTenant(() -> service.releaseToClaimPool(12L, 99L, disposition("release")));
        withTenant(() -> service.supervisorRecycleOwned(13L, 99L, disposition("supervisor-recycle")));
        withTenant(() -> service.supervisorReleaseOwnedToClaimPool(14L, 99L,
                disposition("supervisor-release")));

        assertAll(
                () -> assertNotNull(suspended.getLastActivityAt()),
                () -> assertNotNull(recycled.getLastActivityAt()),
                () -> assertNotNull(ownedForRecycle.getLastActivityAt()),
                () -> assertNotNull(ownedForRelease.getLastActivityAt()),
                () -> assertNull(suspended.getOwnerUserId()),
                () -> assertNull(recycled.getOwnerUserId()),
                () -> assertNull(ownedForRecycle.getOwnerUserId()),
                () -> assertNull(ownedForRelease.getOwnerUserId()),
                () -> assertEquals("public_pool", recycled.getAssignmentStatus()),
                () -> assertEquals("public_pool", ownedForRelease.getAssignmentStatus()),
                () -> assertNull(recycled.getRecycleSourceOwnerUserId()),
                () -> assertNull(ownedForRelease.getRecycleSourceOwnerUserId()),
                () -> assertEquals(20L, ownedForRecycle.getRecycleSourceOwnerUserId()),
                () -> assertNull(ownedForRelease.getQualificationDeadlineAt()));
        verify(leadMapper).updateAfterOwnershipCleared(suspended);
        verify(leadMapper).updateAfterOwnershipCleared(recycled);
        verify(leadMapper).updateAfterOwnershipCleared(ownedForRecycle);
        verify(leadMapper).updateAfterOwnershipCleared(ownedForRelease);
        verify(leadMapper, never()).updateById(any(LeadDO.class));
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

    private LeadDispositionReqVO disposition(String key) {
        LeadDispositionReqVO request = new LeadDispositionReqVO();
        request.setIdempotencyKey(key);
        request.setReason("主管处置");
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
