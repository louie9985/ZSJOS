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
import cn.iocoder.yudao.module.zsjos.dal.mysql.lead.LeadAttachmentMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.lead.LeadIntendedProductMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.lead.LeadMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.lead.LeadAssignmentHistoryMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.lead.LeadAssignmentRuleMapper;
import org.springframework.context.ApplicationEventPublisher;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static cn.iocoder.yudao.module.zsjos.enums.LeadConstants.DICT_CATEGORY;
import static cn.iocoder.yudao.module.zsjos.enums.LeadConstants.DICT_SOURCE_CHANNEL;
import static cn.iocoder.yudao.module.zsjos.enums.LeadNotifySceneConstants.ACCEPTED;
import static cn.iocoder.yudao.module.zsjos.enums.LeadNotifySceneConstants.ASSIGNED;
import static cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants.LEAD_PERMISSION_DENIED;
import static cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants.LEAD_QUALIFICATION_DISPOSITION_INVALID;
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
    private LeadNotifyEventPublisher notifyEventPublisher;
    @Mock
    private LeadAssignmentRuleMapper ruleMapper;
    @Mock
    private LeadDispatchRedisRepository dispatchRedisRepository;

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
        verify(notifyEventPublisher).publish(eq(ACCEPTED), eq(1L), eq("lead-accepted:88"), eq(10L),
                any(), any());
    }

    @Test
    void claimPoolAllowsQueryAllAdministratorAndLoadsRelationsInBatches() {
        LeadClaimPoolPageReqVO reqVO = request();
        LeadDO lead = lead();
        when(securityFrameworkService.hasPermission("zsjos:lead:query-all")).thenReturn(true);
        when(leadMapper.selectPublicPoolPage(reqVO)).thenReturn(new PageResult<>(List.of(lead), 1L));
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
        when(securityFrameworkService.hasPermission("zsjos:lead:query-all")).thenReturn(true);
        when(leadMapper.selectPublicPoolPage(reqVO)).thenReturn(new PageResult<>(List.of(lead), 1L));
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
    void claimPoolAllowsEligibleSalesUser() {
        LeadClaimPoolPageReqVO reqVO = request();
        when(securityFrameworkService.hasPermission("zsjos:lead:query-all")).thenReturn(false);
        when(assignmentService.getEligibleSalesUsers()).thenReturn(List.of(salesUser(10L)));
        when(leadMapper.selectPublicPoolPage(reqVO)).thenReturn(PageResult.empty());

        PageResult<LeadPendingRespVO> result = service.getClaimPoolPage(reqVO, 10L);

        assertEquals(0L, result.getTotal());
        verify(leadMapper).selectPublicPoolPage(reqVO);
    }

    @Test
    void claimPoolRejectsUserWithoutSalesOrQueryAllAccess() {
        when(securityFrameworkService.hasPermission("zsjos:lead:query-all")).thenReturn(false);
        when(assignmentService.getEligibleSalesUsers()).thenReturn(List.of(salesUser(10L)));

        ServiceException error = assertThrows(ServiceException.class,
                () -> service.getClaimPoolPage(request(), 20L));

        assertEquals(LEAD_PERMISSION_DENIED.getCode(), error.getCode());
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
        verify(notifyEventPublisher).publish(eq(ASSIGNED), eq(1L), eq("lead-dispatch:88"), eq(0L),
                any(), any());
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
