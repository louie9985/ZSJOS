package cn.iocoder.yudao.module.zsjos.service.lead;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.common.biz.system.dict.dto.DictDataRespDTO;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.infra.api.file.FileApi;
import cn.iocoder.yudao.module.system.api.dict.DictDataApi;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.followup.LeadFollowUpCreateReqVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.followup.LeadFollowUpRespVO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.LeadDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.LeadFollowUpRecordDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.OpportunityDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.OpportunityFollowUpRecordDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.event.BusinessEventMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.lead.LeadFollowUpImageMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.lead.LeadFollowUpRecordMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.lead.LeadMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.lead.OpportunityFollowUpImageMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.lead.OpportunityFollowUpRecordMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.lead.OpportunityMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants.LEAD_FOLLOW_UP_STATE_INVALID;
import static cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants.LEAD_FOLLOW_UP_TIME_INVALID;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LeadFollowUpServiceImplTest {
    @InjectMocks private LeadFollowUpServiceImpl service;
    @Mock private cn.iocoder.yudao.module.zsjos.service.performance.PerformanceSnapshotService performanceSnapshotService;
    @Mock private LeadMapper leadMapper;
    @Mock private LeadFollowUpRecordMapper recordMapper;
    @Mock private LeadFollowUpImageMapper imageMapper;
    @Mock private BusinessEventMapper eventMapper;
    @Mock private DictDataApi dictDataApi;
    @Mock private AdminUserApi adminUserApi;
    @Mock private FileApi fileApi;
    @Mock private LeadAttachmentService attachmentService;
    @Mock private LeadLifecycleTaskService lifecycleTaskService;
    @Mock private LeadNotifyEventPublisher notifyEventPublisher;
    @Mock private OpportunityMapper opportunityMapper;
    @Mock private OpportunityFollowUpRecordMapper opportunityRecordMapper;
    @Mock private OpportunityFollowUpImageMapper opportunityImageMapper;
    @Mock private LeadCollaborationService collaborationService;

    @Test
    void createRejectsLeadOutsideOwnedSubmittedCycle() {
        LeadDO lead = validLead();
        lead.setAssignmentStatus("public_pool");
        when(leadMapper.selectByIdForUpdate(1L, 9L)).thenReturn(lead);
        try (MockedStatic<TenantContextHolder> tenant = mockStatic(TenantContextHolder.class)) {
            tenant.when(TenantContextHolder::getRequiredTenantId).thenReturn(9L);
            ServiceException error = assertThrows(ServiceException.class,
                    () -> service.create(1L, 20L, request(LocalDateTime.now().plusHours(1))));
            assertEquals(LEAD_FOLLOW_UP_STATE_INVALID.getCode(), error.getCode());
        }
    }

    @Test
    void createRejectsNextTimeThatIsNotFuture() {
        LeadDO lead = validLead();
        when(leadMapper.selectByIdForUpdate(1L, 9L)).thenReturn(lead);
        when(recordMapper.selectByIdempotencyKey("request-1")).thenReturn(null);
        try (MockedStatic<TenantContextHolder> tenant = mockStatic(TenantContextHolder.class)) {
            tenant.when(TenantContextHolder::getRequiredTenantId).thenReturn(9L);
            ServiceException error = assertThrows(ServiceException.class,
                    () -> service.create(1L, 20L, request(LocalDateTime.now().minusMinutes(1))));
            assertEquals(LEAD_FOLLOW_UP_TIME_INVALID.getCode(), error.getCode());
        }
    }

    @Test
    void invalidLeadRejectsNewFollowUp() {
        LeadDO lead = validLead();
        lead.setStatus("invalid"); lead.setAssignmentStatus("owned");
        when(leadMapper.selectByIdForUpdate(1L, 9L)).thenReturn(lead);

        ServiceException error = assertThrows(ServiceException.class,
                () -> withTenant(() -> service.create(1L, 20L, request(LocalDateTime.now().plusHours(1)))));

        assertEquals(LEAD_FOLLOW_UP_STATE_INVALID.getCode(), error.getCode());
        verifyNoInteractions(lifecycleTaskService);
    }

    @Test
    void submittedLeadFollowUpUsesLeadReminderScope() {
        LeadDO lead = validLead();
        stubSuccessfulCreate(lead);
        doAnswer(invocation -> {
            invocation.<LeadFollowUpRecordDO>getArgument(0).setId(40L);
            return 1;
        }).when(recordMapper).insert(any(LeadFollowUpRecordDO.class));

        withTenant(() -> service.create(1L, 20L, request(LocalDateTime.now().plusHours(1))));

        verify(lifecycleTaskService).replaceFollowUpReminder(eq(1L), eq(20L), eq("lead"), eq(40L),
                any(LocalDateTime.class), any(LocalDateTime.class));
    }

    @Test
    void createDoesNotStoreCategoryKeyWhenDictionaryLabelIsMissing() {
        LeadDO lead = validLead();
        stubSuccessfulCreate(lead);
        when(dictDataApi.getDictDataList("zsjos_lead_category")).thenReturn(List.of());
        doAnswer(invocation -> {
            invocation.<LeadFollowUpRecordDO>getArgument(0).setId(40L);
            return 1;
        }).when(recordMapper).insert(any(LeadFollowUpRecordDO.class));

        withTenant(() -> service.create(1L, 20L, request(LocalDateTime.now().plusHours(1))));

        ArgumentCaptor<LeadFollowUpRecordDO> captor = ArgumentCaptor.forClass(LeadFollowUpRecordDO.class);
        verify(recordMapper).insert(captor.capture());
        assertNull(captor.getValue().getCategoryBeforeLabelSnapshot());
        assertNull(captor.getValue().getCategoryAfterLabelSnapshot());
    }

    @Test
    void validLeadFollowUpBelongsToOpportunityAndUpdatesReminder() {
        LeadDO lead = validLead();
        lead.setStatus("valid"); lead.setAssignmentStatus("owned");
        stubSuccessfulCreate(lead);
        OpportunityDO opportunity = new OpportunityDO();
        opportunity.setId(30L); opportunity.setStatus("open");
        when(opportunityMapper.selectByLeadId(1L)).thenReturn(opportunity);
        doAnswer(invocation -> {
            invocation.<OpportunityFollowUpRecordDO>getArgument(0).setId(50L);
            return 1;
        }).when(opportunityRecordMapper).insert(any(OpportunityFollowUpRecordDO.class));

        LeadFollowUpRespVO result = withTenant(() -> service.create(1L, 20L,
                request(LocalDateTime.now().plusHours(1))));

        assertEquals("opportunity", result.getRecordScope());
        assertEquals(30L, result.getOpportunityId());
        assertEquals("following", opportunity.getStatus());
        verify(opportunityRecordMapper).insert(any(OpportunityFollowUpRecordDO.class));
        verify(lifecycleTaskService).replaceFollowUpReminder(eq(1L), eq(20L), eq("opportunity"), eq(50L),
                any(LocalDateTime.class), any(LocalDateTime.class));
    }

    @Test
    void validLeadFollowUpCompletesFirstFollowUpOfCurrentCycle() {
        LeadDO lead = validLead();
        lead.setStatus("valid"); lead.setAssignmentStatus("owned");
        lead.setCurrentAssignmentHistoryId(88L);
        lead.setCurrentAssignmentFirstFollowUpAt(null);
        stubSuccessfulCreate(lead);
        OpportunityDO opportunity = new OpportunityDO();
        opportunity.setId(30L); opportunity.setStatus("open");
        when(opportunityMapper.selectByLeadId(1L)).thenReturn(opportunity);
        when(lifecycleTaskService.completeFirstFollowUpTask(eq(88L), any(LocalDateTime.class))).thenReturn(true);
        doAnswer(invocation -> {
            invocation.<OpportunityFollowUpRecordDO>getArgument(0).setId(50L);
            return 1;
        }).when(opportunityRecordMapper).insert(any(OpportunityFollowUpRecordDO.class));

        withTenant(() -> service.create(1L, 20L, request(LocalDateTime.now().plusHours(1))));

        verify(lifecycleTaskService).completeFirstFollowUpTask(eq(88L), any(LocalDateTime.class));
        assertNotNull(lead.getCurrentAssignmentFirstFollowUpAt());
    }

    @Test
    void validLeadFollowUpRecordsFactWhenTaskIsMissingOrCompleted() {
        LeadDO lead = validLead();
        lead.setStatus("valid"); lead.setAssignmentStatus("owned");
        lead.setCurrentAssignmentHistoryId(88L);
        lead.setCurrentAssignmentFirstFollowUpAt(null);
        stubSuccessfulCreate(lead);
        OpportunityDO opportunity = new OpportunityDO();
        opportunity.setId(30L); opportunity.setStatus("open");
        when(opportunityMapper.selectByLeadId(1L)).thenReturn(opportunity);
        when(lifecycleTaskService.completeFirstFollowUpTask(eq(88L), any(LocalDateTime.class))).thenReturn(false);
        doAnswer(invocation -> {
            invocation.<OpportunityFollowUpRecordDO>getArgument(0).setId(50L);
            return 1;
        }).when(opportunityRecordMapper).insert(any(OpportunityFollowUpRecordDO.class));

        withTenant(() -> service.create(1L, 20L, request(LocalDateTime.now().plusHours(1))));

        assertNotNull(lead.getCurrentAssignmentFirstFollowUpAt());
    }

    @Test
    void submittedFollowUpWithoutTaskStillRecordsFirstFact() {
        LeadDO lead = validLead();
        stubSuccessfulCreate(lead);
        doAnswer(inv -> { inv.<LeadFollowUpRecordDO>getArgument(0).setId(40L); return 1; })
                .when(recordMapper).insert(any(LeadFollowUpRecordDO.class));
        var response = withTenant(() -> service.create(1L, 20L, request(LocalDateTime.now().plusHours(1))));
        assertEquals(response.getOccurredAt(), lead.getCurrentAssignmentFirstFollowUpAt());
        assertTrue(response.getFirstInAssignment());
        verify(lifecycleTaskService).completeFirstFollowUpTask(88L, response.getOccurredAt());
        verify(lifecycleTaskService, never()).createQualificationTask(any(), any(), any());
    }

    @Test
    void migratedCurrentCycleFirstRecordWinsOverNewSubmission() {
        LeadDO lead = validLead();
        stubSuccessfulCreate(lead);
        LocalDateTime firstAt = LocalDateTime.now().minusDays(2);
        LeadFollowUpRecordDO first = new LeadFollowUpRecordDO();
        first.setId(39L); first.setOccurredAt(firstAt);
        when(recordMapper.selectFirstByAssignment(1L, 88L, 20L)).thenReturn(first);
        doAnswer(inv -> { inv.<LeadFollowUpRecordDO>getArgument(0).setId(40L); return 1; })
                .when(recordMapper).insert(any(LeadFollowUpRecordDO.class));
        var response = withTenant(() -> service.create(1L, 20L, request(LocalDateTime.now().plusHours(1))));
        assertEquals(firstAt, lead.getCurrentAssignmentFirstFollowUpAt());
        assertFalse(response.getFirstInAssignment());
        assertTrue(first.getFirstInAssignment());
        verify(lifecycleTaskService).completeFirstFollowUpTask(88L, firstAt);
    }

    @Test
    void subsequentFollowUpPreservesExistingFirstTime() {
        LeadDO lead = validLead();
        LocalDateTime firstAt = LocalDateTime.now().minusDays(2);
        lead.setCurrentAssignmentFirstFollowUpAt(firstAt);
        stubSuccessfulCreate(lead);
        doAnswer(inv -> { inv.<LeadFollowUpRecordDO>getArgument(0).setId(40L); return 1; })
                .when(recordMapper).insert(any(LeadFollowUpRecordDO.class));
        withTenant(() -> service.create(1L, 20L, request(LocalDateTime.now().plusHours(1))));
        assertEquals(firstAt, lead.getCurrentAssignmentFirstFollowUpAt());
        verify(recordMapper, never()).selectFirstByAssignment(any(), any(), any());
    }

    @Test
    void collaboratorCannotCompleteOwnersFirstFollowUp() {
        LeadDO lead = validLead(); lead.setOwnerUserId(21L); lead.setStatus("valid");
        stubSuccessfulCreate(lead);
        when(opportunityMapper.selectByLeadId(1L)).thenReturn(new OpportunityDO().setId(30L).setStatus("open"));
        doAnswer(inv -> { inv.<OpportunityFollowUpRecordDO>getArgument(0).setId(50L); return 1; })
                .when(opportunityRecordMapper).insert(any(OpportunityFollowUpRecordDO.class));
        withTenant(() -> service.create(1L, 20L, request(LocalDateTime.now().plusHours(1))));
        assertNull(lead.getCurrentAssignmentFirstFollowUpAt());
        verify(lifecycleTaskService, never()).completeFirstFollowUpTask(any(), any());
    }

    @Test
    void bothFollowUpPathsFreezeBeforeAfterAndUpdateLead() {
        for (boolean opportunityPath : List.of(false, true)) {
            reset(leadMapper, recordMapper, opportunityMapper, opportunityRecordMapper, dictDataApi,
                    adminUserApi, attachmentService, imageMapper, opportunityImageMapper, lifecycleTaskService);
            LeadDO lead = validLead().setSalesStage("contacted").setSalesStageLabelSnapshot("旧名称");
            stubSuccessfulCreate(lead);
            when(dictDataApi.getDictDataList(LeadSalesStageSnapshot.DICT_TYPE))
                    .thenReturn(List.of(dict("intent_customer", "选择时名称")));
            if (opportunityPath) {
                lead.setStatus("valid");
                when(opportunityMapper.selectByLeadId(1L)).thenReturn(new OpportunityDO().setId(30L).setStatus("open"));
                doAnswer(inv -> { inv.<OpportunityFollowUpRecordDO>getArgument(0).setId(50L); return 1; })
                        .when(opportunityRecordMapper).insert(any(OpportunityFollowUpRecordDO.class));
            } else {
                doAnswer(inv -> { inv.<LeadFollowUpRecordDO>getArgument(0).setId(40L); return 1; })
                        .when(recordMapper).insert(any(LeadFollowUpRecordDO.class));
            }
            var req = request(LocalDateTime.now().plusHours(1)); req.setSalesStage("intent_customer");
            var saved = withTenant(() -> service.create(1L, 20L, req));
            assertEquals("contacted", saved.getSalesStageBefore());
            assertEquals("旧名称", saved.getSalesStageBeforeLabelSnapshot());
            assertEquals("intent_customer", saved.getSalesStageAfter());
            assertEquals("选择时名称", saved.getSalesStageAfterLabelSnapshot());
            assertEquals(saved.getSalesStageAfter(), lead.getSalesStage());
            assertEquals(saved.getSalesStageAfterLabelSnapshot(), lead.getSalesStageLabelSnapshot());
            verify(leadMapper).updateById(lead);
        }
    }

    @Test
    void omittedStagePreservesHistoricalSnapshotAndIdempotentReplayDoesNotReapplyIt() {
        LeadDO lead = validLead().setSalesStage("contacted").setSalesStageLabelSnapshot("保存时名称");
        stubSuccessfulCreate(lead);
        doAnswer(inv -> { inv.<LeadFollowUpRecordDO>getArgument(0).setId(40L); return 1; })
                .when(recordMapper).insert(any(LeadFollowUpRecordDO.class));
        var response = withTenant(() -> service.create(1L, 20L, request(LocalDateTime.now().plusHours(1))));
        assertEquals("保存时名称", response.getSalesStageAfterLabelSnapshot());
        var captor = ArgumentCaptor.forClass(LeadFollowUpRecordDO.class);
        verify(recordMapper).insert(captor.capture());
        when(recordMapper.selectByIdempotencyKey("request-1")).thenReturn(captor.getValue());
        lead.setSalesStage("intent_customer").setSalesStageLabelSnapshot("后来阶段");
        var replay = withTenant(() -> service.create(1L, 20L, request(LocalDateTime.now().plusHours(1))));
        assertEquals("保存时名称", replay.getSalesStageAfterLabelSnapshot());
        assertEquals("intent_customer", lead.getSalesStage());
        verify(leadMapper, times(1)).updateById(lead);
        verify(dictDataApi, never()).getDictDataList(LeadSalesStageSnapshot.DICT_TYPE);
    }

    @Test
    void wonFollowUpSupportsBothRecordScopesAndOptionalTimeWithoutReopeningDeal() {
        for (Class<?> entity : List.of(LeadDO.class, OpportunityDO.class)) {
            com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(
                    new org.apache.ibatis.builder.MapperBuilderAssistant(
                            new com.baomidou.mybatisplus.core.MybatisConfiguration(), ""), entity);
        }
        for (boolean hasOpportunity : List.of(false, true)) {
            for (boolean scheduled : List.of(false, true)) {
                reset(leadMapper, recordMapper, opportunityMapper, opportunityRecordMapper, dictDataApi,
                        adminUserApi, attachmentService, imageMapper, opportunityImageMapper, lifecycleTaskService);
                LeadDO lead = validLead().setStatus("won").setFollowUpCount(3)
                        .setSalesStage("contacted").setSalesStageLabelSnapshot("旧阶段")
                        .setNextFollowUpAt(LocalDateTime.now().minusDays(1));
                stubSuccessfulCreate(lead);
                when(dictDataApi.getDictDataList(LeadSalesStageSnapshot.DICT_TYPE))
                        .thenReturn(List.of(dict("intent_customer", "新阶段")));
                OpportunityDO opportunity = new OpportunityDO().setId(30L).setStatus("won")
                        .setWonAt(LocalDateTime.now().minusDays(2));
                LocalDateTime wonAt = opportunity.getWonAt();
                when(opportunityMapper.selectByLeadId(1L)).thenReturn(hasOpportunity ? opportunity : null);
                if (hasOpportunity) {
                    doAnswer(inv -> { inv.<OpportunityFollowUpRecordDO>getArgument(0).setId(50L); return 1; })
                            .when(opportunityRecordMapper).insert(any(OpportunityFollowUpRecordDO.class));
                } else {
                    doAnswer(inv -> { inv.<LeadFollowUpRecordDO>getArgument(0).setId(40L); return 1; })
                            .when(recordMapper).insert(any(LeadFollowUpRecordDO.class));
                }
                LocalDateTime next = scheduled ? LocalDateTime.now().plusDays(1) : null;
                var req = request(next); req.setSalesStage("intent_customer");
                var result = withTenant(() -> service.create(1L, 20L, req));
                assertEquals(hasOpportunity ? "opportunity" : "lead", result.getRecordScope());
                assertEquals("won", lead.getStatus());
                assertEquals("won", opportunity.getStatus());
                assertEquals(wonAt, opportunity.getWonAt());
                assertEquals(4, lead.getFollowUpCount());
                assertEquals(next, lead.getNextFollowUpAt());
                assertEquals(next, result.getNextFollowUpAt());
                assertEquals("intent_customer", lead.getSalesStage());
                assertEquals("旧阶段", result.getSalesStageBeforeLabelSnapshot());
                assertEquals("新阶段", result.getSalesStageAfterLabelSnapshot());
                assertNull(lead.getCurrentAssignmentFirstFollowUpAt());
                verify(lifecycleTaskService, never()).completeFirstFollowUpTask(any(), any());
                verify(lifecycleTaskService, never()).createQualificationTask(any(), any(), any());
                verify(lifecycleTaskService).replaceFollowUpReminder(eq(1L), eq(20L),
                        eq(result.getRecordScope()), eq(result.getId()), eq(next), any());
                if (!scheduled) {
                    var update = ArgumentCaptor.forClass(com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper.class);
                    verify(leadMapper).update(isNull(), update.capture());
                    assertTrue(update.getValue().getSqlSet().contains("next_follow_up_at="));
                    assertTrue(update.getValue().getSqlSegment().contains("id ="));
                    assertTrue(update.getValue().getParamNameValuePairs().containsValue(null));
                    assertTrue(update.getValue().getParamNameValuePairs().containsValue(1L));
                    if (hasOpportunity) {
                        verify(opportunityMapper).update(isNull(), update.capture());
                        assertTrue(update.getValue().getSqlSet().contains("next_follow_up_at="));
                        assertTrue(update.getValue().getSqlSegment().contains("id ="));
                        assertTrue(update.getValue().getParamNameValuePairs().containsValue(null));
                        assertTrue(update.getValue().getParamNameValuePairs().containsValue(30L));
                    }
                }
                if (hasOpportunity) {
                    var saved = ArgumentCaptor.forClass(OpportunityFollowUpRecordDO.class);
                    verify(opportunityRecordMapper).insert(saved.capture());
                    when(opportunityRecordMapper.selectByIdempotencyKey("request-1")).thenReturn(saved.getValue());
                } else {
                    var saved = ArgumentCaptor.forClass(LeadFollowUpRecordDO.class);
                    verify(recordMapper).insert(saved.capture());
                    when(recordMapper.selectByIdempotencyKey("request-1")).thenReturn(saved.getValue());
                }
                withTenant(() -> service.create(1L, 20L, req));
                assertEquals(4, lead.getFollowUpCount());
                verify(leadMapper, times(1)).updateById(lead);
                verify(lifecycleTaskService, times(1)).replaceFollowUpReminder(any(), any(), any(), any(), eq(next), any());
            }
        }
    }

    @Test
    void missingTimeRemainsInvalidBeforeDealAndPastTimeRemainsInvalidAfterDeal() {
        for (String status : List.of("submitted", "valid", "won")) {
            LeadDO lead = validLead().setStatus(status);
            when(leadMapper.selectByIdForUpdate(1L, 9L)).thenReturn(lead);
            var req = request("won".equals(status) ? LocalDateTime.now().minusSeconds(1) : null);
            ServiceException error = assertThrows(ServiceException.class,
                    () -> withTenant(() -> service.create(1L, 20L, req)));
            assertEquals(LEAD_FOLLOW_UP_TIME_INVALID.getCode(), error.getCode());
        }
        verifyNoInteractions(lifecycleTaskService);
    }

    @Test
    void forbiddenStatesAndPendingDealRemainRejected() {
        for (String status : List.of("invalid", "closed", "suspended", "valid")) {
            reset(leadMapper, recordMapper, opportunityMapper, opportunityRecordMapper, dictDataApi,
                    adminUserApi, attachmentService, imageMapper, opportunityImageMapper);
            LeadDO lead = validLead().setStatus(status);
            if ("valid".equals(status)) {
                stubSuccessfulCreate(lead);
                when(opportunityMapper.selectByLeadId(1L)).thenReturn(
                        new OpportunityDO().setId(30L).setStatus("deal_pending_approval"));
            } else {
                when(leadMapper.selectByIdForUpdate(1L, 9L)).thenReturn(lead);
            }
            ServiceException error = assertThrows(ServiceException.class,
                    () -> withTenant(() -> service.create(1L, 20L, request(LocalDateTime.now().plusDays(1)))));
            assertEquals(LEAD_FOLLOW_UP_STATE_INVALID.getCode(), error.getCode());
            verify(recordMapper, never()).insert(any(LeadFollowUpRecordDO.class));
            verify(opportunityRecordMapper, never()).insert(any(OpportunityFollowUpRecordDO.class));
        }
        verifyNoInteractions(lifecycleTaskService);
    }

    private void stubSuccessfulCreate(LeadDO lead) {
        when(leadMapper.selectByIdForUpdate(1L, 9L)).thenReturn(lead);
        when(recordMapper.selectByIdempotencyKey("request-1")).thenReturn(null);
        when(opportunityRecordMapper.selectByIdempotencyKey("request-1")).thenReturn(null);
        when(dictDataApi.getDictDataList(anyString())).thenAnswer(invocation -> {
            String type = invocation.getArgument(0);
            if ("zsjos_lead_follow_up_method".equals(type)) return List.of(dict("phone", "电话"));
            if ("zsjos_lead_follow_up_result".equals(type)) return List.of(dict("interested", "有意向"));
            return List.of(dict("a", "A 类"));
        });
        when(adminUserApi.getUser(20L)).thenReturn(null);
        when(attachmentService.validateReferences(anyList(), eq(20L))).thenReturn(Map.of());
        lenient().when(imageMapper.selectListByRecordIds(anyList())).thenReturn(List.of());
        lenient().when(opportunityImageMapper.selectListByRecordIds(anyList())).thenReturn(List.of());
    }

    private DictDataRespDTO dict(String value, String label) {
        DictDataRespDTO item = new DictDataRespDTO();
        item.setValue(value); item.setLabel(label); item.setStatus(0);
        return item;
    }

    private <T> T withTenant(java.util.function.Supplier<T> supplier) {
        try (MockedStatic<TenantContextHolder> tenant = mockStatic(TenantContextHolder.class)) {
            tenant.when(TenantContextHolder::getRequiredTenantId).thenReturn(9L);
            return supplier.get();
        }
    }

    private LeadDO validLead() {
        LeadDO lead = new LeadDO();
        lead.setId(1L); lead.setStatus("submitted"); lead.setAssignmentStatus("owned");
        lead.setOwnerUserId(20L); lead.setCurrentAssignmentHistoryId(88L); lead.setLeadCategory("a");
        return lead;
    }

    private LeadFollowUpCreateReqVO request(LocalDateTime nextAt) {
        LeadFollowUpCreateReqVO request = new LeadFollowUpCreateReqVO();
        request.setMethod("phone"); request.setResult("interested"); request.setLeadCategory("a");
        request.setRemark("已联系客户");
        request.setNextFollowUpAt(nextAt); request.setIdempotencyKey("request-1");
        return request;
    }
}
