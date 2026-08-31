package cn.iocoder.yudao.module.zsjos.service.lead;

import cn.iocoder.yudao.framework.common.biz.system.dict.dto.DictDataRespDTO;
import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.security.core.service.SecurityFrameworkService;
import cn.iocoder.yudao.module.system.api.dict.DictDataApi;
import cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.assignment.LeadAssignmentUserRespVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.dispatch.LeadClaimPoolPageReqVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.dispatch.LeadPendingRespVO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.LeadAttachmentDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.LeadDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.LeadIntendedProductDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.LeadAssignmentRuleDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.OpportunityDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.lead.LeadAttachmentMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.lead.LeadIntendedProductMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.lead.LeadMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.lead.LeadAssignmentHistoryMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.lead.LeadAssignmentRuleMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.lead.OpportunityMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.lead.LeadClaimDailyCounterMapper;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.zsjos.service.advancedfilter.AdvancedFilterService;
import org.springframework.context.ApplicationEventPublisher;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static cn.iocoder.yudao.module.zsjos.enums.LeadConstants.DICT_CATEGORY;
import static cn.iocoder.yudao.module.zsjos.enums.LeadConstants.DICT_SOURCE_CHANNEL;
import static cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants.LEAD_PERMISSION_DENIED;
import static cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants.LEAD_QUALIFICATION_DISPOSITION_INVALID;
import static cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants.LEAD_CLAIM_DAILY_LIMIT_REACHED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.times;

@ExtendWith(MockitoExtension.class)
class LeadDispatchServiceImplTest {

    @BeforeEach void setUp() { TenantContextHolder.setTenantId(1L); org.mockito.Mockito.lenient().when(advancedFilterService.matchLeadIds(any())).thenReturn(null); }
    @AfterEach void tearDown() { TenantContextHolder.clear(); }

    @InjectMocks
    private LeadDispatchServiceImpl service;
    @Mock
    private LeadMapper leadMapper;
    @Mock
    private LeadIntendedProductMapper productMapper;
    @Mock
    private LeadAttachmentMapper attachmentMapper;
    @Mock
    private LeadAssignmentService assignmentService;
    @Mock
    private DictDataApi dictDataApi;
    @Mock
    private SecurityFrameworkService securityFrameworkService;
    @Mock
    private LeadAssignmentHistoryMapper historyMapper;
    @Mock
    private LeadLifecycleTaskService lifecycleTaskService;
    @Mock
    private ApplicationEventPublisher applicationEventPublisher;
    @Mock
    private AdvancedFilterService advancedFilterService;
    @Mock
    private LeadNotifyEventPublisher notifyEventPublisher;
    @Mock
    private LeadAssignmentRuleMapper ruleMapper;
    @Mock
    private LeadDispatchRedisRepository dispatchRedisRepository;
    @Mock
    private OpportunityMapper opportunityMapper;
    @Mock
    private LeadAgingPoolService agingPoolService;
    @Mock
    private LeadClaimDailyCounterMapper claimDailyCounterMapper;

    @Test
    void acceptAtomicallyCompletesAssignmentAndCreatesFirstFollowUpTask() {
        LeadDO lead = lead();
        lead.setAssignmentStatus("pending_acceptance");
        lead.setPendingAssigneeUserId(10L);
        lead.setAssignmentAttemptCount(2);
        when(leadMapper.selectById(1L)).thenReturn(lead);
        when(leadMapper.updatePendingResult(1L, 10L, "owned", 10L)).thenReturn(1);
        doAnswer(invocation -> {
            var history = invocation.getArgument(0, cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.LeadAssignmentHistoryDO.class);
            history.setId(88L);
            return 1;
        }).when(historyMapper).insert(any(cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.LeadAssignmentHistoryDO.class));

        service.accept(1L, 10L);

        verify(lifecycleTaskService).completeAssignmentTask(eq(1L), eq(10L), any());
        verify(lifecycleTaskService).createFirstFollowUpTask(eq(1L), eq(10L), eq(88L), any(),
                eq("lead_assignment_accepted"), eq("pending_acceptance"));
        verify(applicationEventPublisher).publishEvent(any(LeadAssignmentRealtimeEvent.class));
        verify(notifyEventPublisher, never()).publish(any(), any(), any(), any(), any(), any());
    }

    @Test
    void claimPoolAllowsQueryAllAdministratorAndLoadsRelationsInBatches() {
        LeadClaimPoolPageReqVO reqVO = request();
        LeadDO lead = lead();
        when(leadMapper.selectPublicPoolPage(reqVO, null, null)).thenReturn(new PageResult<>(List.of(lead), 1L));
        when(productMapper.selectListByLeadIds(List.of(1L))).thenReturn(List.of(product()));
        when(attachmentMapper.selectListByLeadIds(List.of(1L))).thenReturn(List.of(attachment()));
        when(dictDataApi.getDictDataList(DICT_SOURCE_CHANNEL)).thenReturn(List.of(dict("douyin", "抖音")));
        when(dictDataApi.getDictDataList(DICT_CATEGORY)).thenReturn(List.of(dict("adult", "成人学历")));

        PageResult<LeadPendingRespVO> result = service.getClaimPoolPage(reqVO, 99L);

        LeadPendingRespVO item = result.getList().getFirst();
        assertEquals(1L, result.getTotal());
        assertNotEquals("张三丰", item.getMaskedName());
        assertTrue(item.getMaskedName().contains("*"));
        assertNotEquals("13800138000", item.getMaskedMobile());
        assertNotEquals("wechat-full", item.getMaskedWechatId());
        assertEquals(List.of("课程 A"), item.getIntendedProducts());
        assertEquals("课程 A", item.getPrimaryIntendedProduct());
        assertEquals("douyin", item.getSourceChannel());
        assertEquals("抖音", item.getSourceChannelLabel());
        assertEquals("adult", item.getLeadCategory());
        assertEquals("成人学历", item.getLeadCategoryLabel());
        assertEquals(List.of("https://example.test/a.jpg"), item.getAttachmentUrls());
        verify(assignmentService, never()).getEligibleSalesUsers();
        verify(productMapper, never()).selectListByLeadId(1L);
        verify(attachmentMapper, never()).selectListByLeadId(1L);
    }

    @Test
    void claimPoolPreservesKeysAndLeavesMissingLabelsEmpty() {
        LeadClaimPoolPageReqVO reqVO = request();
        LeadDO lead = lead();
        when(leadMapper.selectPublicPoolPage(reqVO, null, null)).thenReturn(new PageResult<>(List.of(lead), 1L));
        when(productMapper.selectListByLeadIds(List.of(1L))).thenReturn(List.of());
        when(attachmentMapper.selectListByLeadIds(List.of(1L))).thenReturn(List.of());
        when(dictDataApi.getDictDataList(DICT_SOURCE_CHANNEL)).thenReturn(List.of());
        when(dictDataApi.getDictDataList(DICT_CATEGORY)).thenReturn(List.of());

        LeadPendingRespVO item = service.getClaimPoolPage(reqVO, 99L).getList().getFirst();

        assertEquals("douyin", item.getSourceChannel());
        assertEquals(null, item.getSourceChannelLabel());
        assertEquals("adult", item.getLeadCategory());
        assertEquals(null, item.getLeadCategoryLabel());
    }

    @Test
    void claimPoolAllowsAuthorizedReadOnlyUserWithoutSalesQualification() {
        LeadClaimPoolPageReqVO reqVO = request();
        when(leadMapper.selectPublicPoolPage(reqVO, null, null)).thenReturn(PageResult.empty());

        PageResult<LeadPendingRespVO> result = service.getClaimPoolPage(reqVO, 10L);

        assertEquals(0L, result.getTotal());
        verify(leadMapper).selectPublicPoolPage(reqVO, null, null);
    }

    @Test
    void claimRejectsWhenBeijingDailyLimitReached() {
        when(assignmentService.getEligibleSalesUsers()).thenReturn(List.of(salesUser(10L)));
        when(ruleMapper.selectByCode("default")).thenReturn(rule());
        when(leadMapper.selectById(1L)).thenReturn(lead());
        when(leadMapper.updatePublicPoolToOwned(1L, 10L)).thenReturn(1);
        when(claimDailyCounterMapper.reserve(eq(1L), eq(10L), any(), eq(5))).thenReturn(0);

        ServiceException error = assertThrows(ServiceException.class, () -> service.claim(1L, 10L));

        assertEquals(LEAD_CLAIM_DAILY_LIMIT_REACHED.getCode(), error.getCode());
        verify(leadMapper).updatePublicPoolToOwned(1L, 10L);
    }

    @Test
    void claimCreatesFollowUpTaskWithoutBusinessNotification() {
        LeadDO lead = lead();
        lead.setAssignmentStatus("public_pool");
        when(assignmentService.getEligibleSalesUsers()).thenReturn(List.of(salesUser(10L)));
        when(ruleMapper.selectByCode("default")).thenReturn(rule());
        when(leadMapper.selectById(1L)).thenReturn(lead);
        when(leadMapper.updatePublicPoolToOwned(1L, 10L)).thenReturn(1);
        when(claimDailyCounterMapper.reserve(eq(1L), eq(10L), any(), eq(5))).thenReturn(1);
        doAnswer(invocation -> {
            var history = invocation.getArgument(0,
                    cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.LeadAssignmentHistoryDO.class);
            history.setId(88L);
            return 1;
        }).when(historyMapper).insert(any(cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.LeadAssignmentHistoryDO.class));

        service.claim(1L, 10L);

        verify(lifecycleTaskService).createFirstFollowUpTask(eq(1L), eq(10L), eq(88L), any(),
                eq("lead_claimed"), eq("public_pool"));
        verify(notifyEventPublisher, never()).publish(any(), any(), any(), any(), any(), any());
    }

    @Test
    void retryAssignsOnlyOnlineAcceptingSalesAndReservesAtomically() {
        LeadDO lead = retryableLead();
        when(leadMapper.selectRetryableUnassignedAuto()).thenReturn(List.of(lead));
        when(ruleMapper.selectByCode("default")).thenReturn(rule());
        when(historyMapper.selectTriedSalesUserIds(1L)).thenReturn(List.of());
        when(assignmentService.getEligibleSalesUsers()).thenReturn(List.of(salesUser(10L)));
        when(dispatchRedisRepository.poolSize()).thenReturn(1L);
        when(dispatchRedisRepository.rotateNext()).thenReturn(10L);
        when(dispatchRedisRepository.isOnline(10L)).thenReturn(true);
        when(dispatchRedisRepository.isAccepting(10L)).thenReturn(true);
        when(dispatchRedisRepository.tryReserve(1L, 10L, 120)).thenReturn(true);
        when(leadMapper.updateUnassignedToPending(eq(1L), eq(10L), any(), eq(1))).thenReturn(1);
        doAnswer(invocation -> {
            var history = invocation.getArgument(0, cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.LeadAssignmentHistoryDO.class);
            history.setId(88L);
            return 1;
        }).when(historyMapper).insert(any(cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.LeadAssignmentHistoryDO.class));

        assertEquals(1, service.processUnassignedRetries());

        verify(dispatchRedisRepository).tryReserve(1L, 10L, 120);
        verify(lifecycleTaskService).createAssignmentTask(eq(1L), eq(10L), eq(88L), any(), eq("auto"));
        verify(applicationEventPublisher).publishEvent(any(LeadAssignmentRealtimeEvent.class));
        verify(notifyEventPublisher, never()).publish(any(), any(), any(), any(), any(), any());
    }

    @Test
    void retryMovesLeadToPoolAfterThreeRoundsOfPausedSales() {
        LeadDO lead = retryableLead();
        when(leadMapper.selectRetryableUnassignedAuto()).thenReturn(List.of(lead));
        when(ruleMapper.selectByCode("default")).thenReturn(rule());
        when(historyMapper.selectTriedSalesUserIds(1L)).thenReturn(List.of());
        when(assignmentService.getEligibleSalesUsers()).thenReturn(List.of(salesUser(10L)));
        when(dispatchRedisRepository.poolSize()).thenReturn(1L);
        when(dispatchRedisRepository.rotateNext()).thenReturn(10L);
        when(dispatchRedisRepository.isOnline(10L)).thenReturn(true);
        when(dispatchRedisRepository.isAccepting(10L)).thenReturn(false);

        assertEquals(1, service.processUnassignedRetries());

        verify(dispatchRedisRepository, times(3)).rotateNext();
        verify(dispatchRedisRepository, never()).tryReserve(any(), any(), anyInt());
        verify(leadMapper).updateById(lead);
        assertEquals("public_pool", lead.getAssignmentStatus());
    }

    @Test
    void legacyAdminTransferRejectsSuspendedLead() {
        LeadDO lead = lead();
        lead.setStatus("suspended");
        lead.setAssignmentStatus("owned");
        lead.setOwnerUserId(10L);
        when(assignmentService.getEligibleSalesUsers()).thenReturn(List.of(salesUser(20L)));
        when(leadMapper.selectById(1L)).thenReturn(lead);

        ServiceException error = assertThrows(ServiceException.class,
                () -> service.adminTransfer(1L, 20L, 99L));

        assertEquals(LEAD_QUALIFICATION_DISPOSITION_INVALID.getCode(), error.getCode());
        verify(leadMapper, never()).updateById(any(LeadDO.class));
    }

    @Test
    void adminTransferSynchronizesOpportunityOwner() {
        LeadDO lead = lead();
        lead.setStatus("valid"); lead.setAssignmentStatus("owned"); lead.setOwnerUserId(10L);
        OpportunityDO opportunity = new OpportunityDO();
        opportunity.setId(30L); opportunity.setLeadId(1L); opportunity.setOwnerUserId(10L);
        when(assignmentService.getEligibleSalesUsers()).thenReturn(List.of(salesUser(20L)));
        when(leadMapper.selectById(1L)).thenReturn(lead);
        when(opportunityMapper.selectByLeadId(1L)).thenReturn(opportunity);
        doAnswer(invocation -> {
            var history = invocation.getArgument(0, cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.LeadAssignmentHistoryDO.class);
            history.setId(88L);
            return 1;
        }).when(historyMapper).insert(any(cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.LeadAssignmentHistoryDO.class));

        service.adminTransfer(1L, 20L, 99L);

        assertEquals(20L, lead.getOwnerUserId());
        assertEquals(20L, opportunity.getOwnerUserId());
        verify(opportunityMapper).updateById(opportunity);
    }

    @Test
    void approvedTransferInvalidatesWhenOriginalOwnerChanged() {
        LeadDO lead = lead();
        lead.setStatus("valid"); lead.setAssignmentStatus("owned"); lead.setOwnerUserId(11L);
        when(assignmentService.getEligibleSalesUsers()).thenReturn(List.of(salesUser(20L)));
        when(leadMapper.selectByIdForUpdate(1L, 1L)).thenReturn(lead);

        LeadDispatchService.TransferAttemptResult result = service.tryAdminTransfer(1L, 10L, 20L, 20L, "审批通过");

        assertEquals(false, result.transferred());
        assertEquals("客资状态或归属已变化", result.reason());
        verify(leadMapper, never()).updateById(any(LeadDO.class));
    }

    @Test
    void approvedTransferSucceedsWhenOriginalOwnerStillMatches() {
        LeadDO lead = lead();
        lead.setStatus("valid"); lead.setAssignmentStatus("owned"); lead.setOwnerUserId(10L);
        when(assignmentService.getEligibleSalesUsers()).thenReturn(List.of(salesUser(20L)));
        when(leadMapper.selectByIdForUpdate(1L, 1L)).thenReturn(lead);
        doAnswer(invocation -> {
            var history = invocation.getArgument(0,
                    cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.LeadAssignmentHistoryDO.class);
            history.setId(88L);
            return 1;
        }).when(historyMapper).insert(any(cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.LeadAssignmentHistoryDO.class));

        LeadDispatchService.TransferAttemptResult result = service.tryAdminTransfer(1L, 10L, 20L, 20L, "审批通过");

        assertTrue(result.transferred());
        assertEquals(20L, lead.getOwnerUserId());
        verify(leadMapper, times(2)).updateById(lead);
    }

    private static LeadClaimPoolPageReqVO request() {
        LeadClaimPoolPageReqVO reqVO = new LeadClaimPoolPageReqVO();
        reqVO.setPageNo(1);
        reqVO.setPageSize(12);
        return reqVO;
    }

    private static LeadDO lead() {
        LeadDO lead = new LeadDO();
        lead.setId(1L);
        lead.setSubmittedName("张三丰");
        lead.setSubmittedMobile("13800138000");
        lead.setSubmittedWechatId("wechat-full");
        lead.setProvinceName("浙江省");
        lead.setCityName("杭州市");
        lead.setSourceChannelId("douyin");
        lead.setLeadCategory("adult");
        lead.setRemark("完整备注");
        return lead;
    }

    private static LeadDO retryableLead() {
        LeadDO lead = lead();
        lead.setDispatchMode("auto");
        lead.setAssignmentStatus("unassigned");
        lead.setAssignmentRuleSnapshot("{\"acceptTimeoutSeconds\":120,\"maxAttempts\":5}");
        return lead;
    }

    private static LeadAssignmentRuleDO rule() {
        LeadAssignmentRuleDO rule = new LeadAssignmentRuleDO();
        rule.setId(7L);
        rule.setCode("default");
        rule.setStrategyType("global_round_robin");
        rule.setStatus(0);
        rule.setConfigJson("{\"acceptTimeoutSeconds\":120,\"maxAttempts\":5}");
        return rule;
    }

    private static LeadIntendedProductDO product() {
        LeadIntendedProductDO product = new LeadIntendedProductDO();
        product.setLeadId(1L);
        product.setProductNameSnapshot("课程 A");
        product.setIsPrimary(true);
        product.setSort(1);
        return product;
    }

    private static LeadAttachmentDO attachment() {
        LeadAttachmentDO attachment = new LeadAttachmentDO();
        attachment.setLeadId(1L);
        attachment.setFileUrl("https://example.test/a.jpg");
        attachment.setSort(1);
        return attachment;
    }

    private static DictDataRespDTO dict(String value, String label) {
        DictDataRespDTO item = new DictDataRespDTO();
        item.setValue(value);
        item.setLabel(label);
        return item;
    }

    private static LeadAssignmentUserRespVO salesUser(Long id) {
        LeadAssignmentUserRespVO user = new LeadAssignmentUserRespVO();
        user.setId(id);
        return user;
    }
}
