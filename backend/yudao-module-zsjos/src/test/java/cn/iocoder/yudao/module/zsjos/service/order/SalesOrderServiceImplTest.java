package cn.iocoder.yudao.module.zsjos.service.order;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.bpm.api.task.BpmProcessInstanceApi;
import cn.iocoder.yudao.module.bpm.api.task.BpmProcessTaskApi;
import cn.iocoder.yudao.module.infra.api.file.FileApi;
import cn.iocoder.yudao.module.system.api.dict.DictDataApi;
import cn.iocoder.yudao.module.system.api.ip.AreaApi;
import cn.iocoder.yudao.module.system.api.dept.DeptApi;
import cn.iocoder.yudao.module.system.api.notify.NotifyBusinessEventApi;
import cn.iocoder.yudao.module.system.api.ip.dto.AreaRespDTO;
import cn.iocoder.yudao.module.zsjos.controller.admin.order.vo.SalesOrderSubmitReqVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.order.vo.SalesOrderMyPageReqVO;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.LeadAppealDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.LeadDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.OpportunityDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.order.*;
import cn.iocoder.yudao.module.zsjos.dal.mysql.lead.LeadMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.lead.LeadAppealMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.lead.OpportunityMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.order.*;
import cn.iocoder.yudao.module.zsjos.service.lead.product.LeadProductSnapshot;
import cn.iocoder.yudao.module.zsjos.service.product.ZsjosProductSkuService;
import cn.iocoder.yudao.module.zsjos.service.lead.LeadLifecycleTaskService;
import cn.iocoder.yudao.module.zsjos.service.lead.LeadAgingPoolService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static cn.iocoder.yudao.module.bpm.enums.task.BpmProcessInstanceStatusEnum.APPROVE;
import static cn.iocoder.yudao.module.bpm.enums.task.BpmProcessInstanceStatusEnum.REJECT;
import static cn.iocoder.yudao.module.zsjos.enums.LeadConstants.*;
import static cn.iocoder.yudao.module.zsjos.enums.SalesOrderConstants.*;
import static cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SalesOrderServiceImplTest {
    @InjectMocks private SalesOrderServiceImpl service;
    @Mock private SalesOrderMapper orderMapper;
    @Mock private SalesOrderItemMapper itemMapper;
    @Mock private SalesOrderApprovalRoundMapper roundMapper;
    @Mock private SalesOrderApprovalConfigMapper configMapper;
    @Mock private LeadMapper leadMapper;
    @Mock private LeadAppealMapper leadAppealMapper;
    @Mock private OpportunityMapper opportunityMapper;
    @Mock private ZsjosProductSkuService skuService;
    @Mock private SalesOrderObjectPermissionService permissionService;
    @Mock private FileApi fileApi;
    @Mock private AreaApi areaApi;
    @Mock private DictDataApi dictDataApi;
    @Mock private BpmProcessInstanceApi processInstanceApi;
    @Mock private BpmProcessTaskApi processTaskApi;
    @Mock private LeadLifecycleTaskService lifecycleTaskService;
    @Mock private NotifyBusinessEventApi notifyBusinessEventApi;
    @Mock private DeptApi deptApi;
    @Mock private LeadAgingPoolService agingPoolService;

    @BeforeEach void setUp() {
        TenantContextHolder.setTenantId(1L);
        lenient().when(agingPoolService.resolveEffectiveSalesUserId(anyLong(), anyLong()))
                .thenAnswer(invocation -> invocation.getArgument(1));
    }
    @AfterEach void tearDown() { TenantContextHolder.clear(); }

    @Test
    void createZeroAmountOrderStartsDualApprovalAndDefaultsBuyer() {
        mockEligibleLeadAndOpportunity();
        SalesOrderApprovalConfigDO config = new SalesOrderApprovalConfigDO();
        config.setRegistrationDeptId(1030L); config.setFinanceDeptId(1040L);
        when(configMapper.selectCurrent()).thenReturn(config);
        when(permissionService.enabledUsers(1030L)).thenReturn(Set.of(301L, 302L));
        when(permissionService.enabledUsers(1040L)).thenReturn(Set.of(401L));
        when(processInstanceApi.createProcessInstance(eq(20L), any())).thenReturn("process-1");
        doAnswer(invocation -> { ((SalesOrderDO) invocation.getArgument(0)).setId(100L); return 1; }).when(orderMapper).insert(any(SalesOrderDO.class));
        doAnswer(invocation -> { ((SalesOrderApprovalRoundDO) invocation.getArgument(0)).setId(200L); return 1; }).when(roundMapper).insert(any(SalesOrderApprovalRoundDO.class));
        when(skuService.validateLeadProduct("spu-1", false, "sku-1", false)).thenReturn(product());

        Long id = service.createAndSubmit(1L, 20L, request(BigDecimal.ZERO, "13800138000", null));

        assertEquals(100L, id);
        ArgumentCaptor<SalesOrderDO> orderCaptor = ArgumentCaptor.forClass(SalesOrderDO.class);
        verify(orderMapper).insert(orderCaptor.capture());
        assertEquals("测试学员", orderCaptor.getValue().getBuyerName());
        assertEquals("天津市", orderCaptor.getValue().getProvinceName());
        assertEquals(new BigDecimal("0.00"), orderCaptor.getValue().getTotalAmount());
        ArgumentCaptor<SalesOrderApprovalRoundDO> roundCaptor = ArgumentCaptor.forClass(SalesOrderApprovalRoundDO.class);
        verify(roundMapper).insert(roundCaptor.capture());
        assertEquals("process-1", roundCaptor.getValue().getProcessInstanceId());
        assertEquals("key-1", roundCaptor.getValue().getSubmissionIdempotencyKey());
        verify(processInstanceApi).createProcessInstance(eq(20L), argThat(req ->
                req.getStartUserSelectAssignees().get(TASK_REGISTRATION).size() == 2
                        && req.getStartUserSelectAssignees().get(TASK_FINANCE).size() == 1));
    }

    @Test
    void createRejectsNonZeroAmountWithoutVoucher() {
        mockEligibleLeadAndOpportunity();
        when(skuService.validateLeadProduct("spu-1", false, "sku-1", false)).thenReturn(product());

        ServiceException error = assertThrows(ServiceException.class,
                () -> service.createAndSubmit(1L, 20L, request(new BigDecimal("10.00"), "13800138000", null)));

        assertEquals(SALES_ORDER_VOUCHER_REQUIRED.getCode(), error.getCode());
        verify(orderMapper, never()).insert(any(SalesOrderDO.class));
    }

    @Test
    void createRejectsMissingContact() {
        mockEligibleLeadAndOpportunity();

        ServiceException error = assertThrows(ServiceException.class,
                () -> service.createAndSubmit(1L, 20L, request(BigDecimal.ZERO, null, null)));

        assertEquals(SALES_ORDER_CONTACT_REQUIRED.getCode(), error.getCode());
        verifyNoInteractions(skuService);
    }

    @Test
    void createRejectsExistingActiveOrder() {
        mockEligibleLeadAndOpportunity();
        when(orderMapper.selectActiveByLeadId(1L, ACTIVE_ORDER_STATUSES)).thenReturn(new SalesOrderDO());

        ServiceException error = assertThrows(ServiceException.class,
                () -> service.createAndSubmit(1L, 20L, request(BigDecimal.ZERO, "13800138000", null)));

        assertEquals(SALES_ORDER_ACTIVE_DUPLICATE.getCode(), error.getCode());
        verifyNoInteractions(skuService);
    }

    @Test
    void createRejectsLeadWithAppealInProgress() {
        mockEligibleLeadAndOpportunity();
        LeadAppealDO appeal = new LeadAppealDO();
        appeal.setStatus(APPEAL_STATUS_QUALITY_REVIEWING);
        when(leadAppealMapper.selectLatestByLeadId(1L)).thenReturn(appeal);

        ServiceException error = assertThrows(ServiceException.class,
                () -> service.createAndSubmit(1L, 20L, request(BigDecimal.ZERO, "13800138000", null)));

        assertEquals(SALES_ORDER_ENTRY_FORBIDDEN.getCode(), error.getCode());
        verifyNoInteractions(skuService);
    }

    @Test
    void approvedProcessMakesOrderEffectiveAndOpportunityWon() {
        SalesOrderApprovalRoundDO round = new SalesOrderApprovalRoundDO();
        round.setId(200L); round.setOrderId(100L); round.setStatus(ROUND_PENDING);
        SalesOrderDO order = new SalesOrderDO();
        order.setId(100L); order.setOpportunityId(30L); order.setCurrentApprovalRoundId(200L); order.setStatus(STATUS_PENDING_APPROVAL);
        OpportunityDO opportunity = new OpportunityDO(); opportunity.setId(30L); opportunity.setStatus(OPPORTUNITY_STATUS_DEAL_PENDING_APPROVAL);
        when(roundMapper.selectByProcessInstanceId("process-1")).thenReturn(round);
        when(orderMapper.selectByIdForUpdate(100L, 1L)).thenReturn(order);
        when(opportunityMapper.selectById(30L)).thenReturn(opportunity);

        service.handleProcessResult("process-1", APPROVE.getStatus(), null);

        assertEquals(STATUS_EFFECTIVE, order.getStatus()); assertNotNull(order.getEffectiveAt());
        assertEquals(ROUND_APPROVED, round.getStatus()); assertEquals(OPPORTUNITY_STATUS_WON, opportunity.getStatus());
        verify(orderMapper).updateById(order); verify(roundMapper).updateById(round); verify(opportunityMapper).updateById(opportunity);
        verify(lifecycleTaskService).cancelFollowUpReminders(eq(order.getLeadId()), any(), eq("成交订单已生效"));
    }

    @Test
    void rejectedProcessReturnsOriginalOrderForRevision() {
        SalesOrderApprovalRoundDO round = new SalesOrderApprovalRoundDO();
        round.setId(200L); round.setOrderId(100L); round.setStatus(ROUND_PENDING);
        SalesOrderDO order = new SalesOrderDO();
        order.setId(100L); order.setOpportunityId(30L); order.setCurrentApprovalRoundId(200L); order.setStatus(STATUS_PENDING_APPROVAL);
        OpportunityDO opportunity = new OpportunityDO(); opportunity.setId(30L); opportunity.setStatus(OPPORTUNITY_STATUS_DEAL_PENDING_APPROVAL);
        when(roundMapper.selectByProcessInstanceId("process-1")).thenReturn(round);
        when(orderMapper.selectByIdForUpdate(100L, 1L)).thenReturn(order);
        when(opportunityMapper.selectById(30L)).thenReturn(opportunity);

        service.handleProcessResult("process-1", REJECT.getStatus(), "资料需补正");

        assertEquals(STATUS_REVISION_REQUIRED, order.getStatus());
        assertEquals(ROUND_REJECTED, round.getStatus());
        assertEquals("资料需补正", round.getDecisionReason());
        assertEquals(OPPORTUNITY_STATUS_FOLLOWING, opportunity.getStatus());
    }

    @Test
    void myPageUsesSubmitterScopeAndDoesNotLoadHeavyDetails() {
        SalesOrderMyPageReqVO reqVO = new SalesOrderMyPageReqVO();
        reqVO.setPageNo(1); reqVO.setPageSize(20); reqVO.setStatus(STATUS_PENDING_APPROVAL); reqVO.setKeyword("测试");
        SalesOrderDO order = new SalesOrderDO();
        order.setId(100L); order.setCurrentApprovalRoundId(200L); order.setOrderNo("SO-100"); order.setLeadId(1L);
        order.setStatus(STATUS_PENDING_APPROVAL); order.setStudentName("测试学员"); order.setTotalAmount(BigDecimal.TEN);
        SalesOrderApprovalRoundDO round = new SalesOrderApprovalRoundDO(); round.setId(200L); round.setRoundNo(2);
        when(orderMapper.selectMyPage(20L, reqVO)).thenReturn(new PageResult<>(List.of(order), 1L));
        when(roundMapper.selectBatchIds(List.of(200L))).thenReturn(List.of(round));

        var result = service.getMyPage(reqVO, 20L);

        assertEquals(1L, result.getTotal()); assertEquals(1, result.getList().size());
        assertEquals("SO-100", result.getList().getFirst().getOrderNo());
        assertEquals(2, result.getList().getFirst().getApprovalRoundNo());
        verify(orderMapper).selectMyPage(20L, reqVO);
        verifyNoInteractions(itemMapper, fileApi);
    }

    @Test
    void myStatusCountsReturnsAllThreeBusinessStates() {
        when(orderMapper.selectMyCount(20L, null)).thenReturn(7L);
        when(orderMapper.selectMyCount(20L, STATUS_PENDING_APPROVAL)).thenReturn(2L);
        when(orderMapper.selectMyCount(20L, STATUS_REVISION_REQUIRED)).thenReturn(1L);
        when(orderMapper.selectMyCount(20L, STATUS_EFFECTIVE)).thenReturn(4L);

        var result = service.getMyStatusCounts(20L);

        assertEquals(7L, result.getTotal()); assertEquals(2L, result.getPendingApproval());
        assertEquals(1L, result.getRevisionRequired()); assertEquals(4L, result.getEffective());
    }

    @Test
    void reviseKeepsOrderAndStartsNewImmutableRound() {
        SalesOrderDO order = new SalesOrderDO();
        order.setId(100L); order.setLeadId(1L); order.setOpportunityId(30L); order.setStatus(STATUS_REVISION_REQUIRED);
        when(orderMapper.selectByIdForUpdate(100L, 1L)).thenReturn(order);
        mockEligibleLeadAndOpportunity();
        SalesOrderApprovalRoundDO previous = new SalesOrderApprovalRoundDO(); previous.setRoundNo(1);
        when(roundMapper.selectLatestByOrderId(100L)).thenReturn(previous);
        SalesOrderApprovalConfigDO config = new SalesOrderApprovalConfigDO();
        config.setRegistrationDeptId(1030L); config.setFinanceDeptId(1040L);
        when(configMapper.selectCurrent()).thenReturn(config);
        when(permissionService.enabledUsers(1030L)).thenReturn(Set.of(301L));
        when(permissionService.enabledUsers(1040L)).thenReturn(Set.of(401L));
        when(processInstanceApi.createProcessInstance(eq(20L), any())).thenReturn("process-2");
        when(skuService.validateLeadProduct("spu-1", false, "sku-1", false)).thenReturn(product());
        doAnswer(invocation -> { ((SalesOrderApprovalRoundDO) invocation.getArgument(0)).setId(201L); return 1; })
                .when(roundMapper).insert(any(SalesOrderApprovalRoundDO.class));

        service.reviseAndResubmit(100L, 20L, request(BigDecimal.ZERO, "13800138000", null));

        assertEquals(STATUS_PENDING_APPROVAL, order.getStatus());
        assertEquals(201L, order.getCurrentApprovalRoundId());
        verify(itemMapper).deleteByOrderId(100L);
        verify(roundMapper).insert(org.mockito.Mockito.<SalesOrderApprovalRoundDO>argThat(round -> round.getOrderId().equals(100L)
                && round.getRoundNo() == 2 && "process-2".equals(round.getProcessInstanceId())
                && round.getOrderSnapshot() != null));
    }

    @Test
    void continuationSupersedesAOrderAndCreatesImmutableBOwnedOrder() {
        SalesOrderDO original = new SalesOrderDO();
        original.setId(100L); original.setLeadId(1L); original.setOpportunityId(30L);
        original.setSubmitterUserId(10L); original.setStatus(STATUS_REVISION_REQUIRED);
        when(orderMapper.selectByIdForUpdate(100L, 1L)).thenReturn(original);
        mockEligibleLeadAndOpportunity();
        SalesOrderApprovalConfigDO config = new SalesOrderApprovalConfigDO();
        config.setRegistrationDeptId(1030L); config.setFinanceDeptId(1040L);
        when(configMapper.selectCurrent()).thenReturn(config);
        when(permissionService.enabledUsers(1030L)).thenReturn(Set.of(301L));
        when(permissionService.enabledUsers(1040L)).thenReturn(Set.of(401L));
        when(processInstanceApi.createProcessInstance(eq(20L), any())).thenReturn("process-continuation");
        when(skuService.validateLeadProduct("spu-1", false, "sku-1", false)).thenReturn(product());
        doAnswer(invocation -> { ((SalesOrderDO) invocation.getArgument(0)).setId(101L); return 1; })
                .when(orderMapper).insert(any(SalesOrderDO.class));
        doAnswer(invocation -> { ((SalesOrderApprovalRoundDO) invocation.getArgument(0)).setId(201L); return 1; })
                .when(roundMapper).insert(any(SalesOrderApprovalRoundDO.class));

        Long continuationId = service.continueAndSubmit(100L, 20L,
                request(BigDecimal.ZERO, "13800138000", null));

        assertEquals(101L, continuationId);
        assertEquals(STATUS_SUPERSEDED, original.getStatus());
        assertEquals(101L, original.getSupersededByOrderId());
        ArgumentCaptor<SalesOrderDO> continuation = ArgumentCaptor.forClass(SalesOrderDO.class);
        verify(orderMapper).insert(continuation.capture());
        assertEquals(20L, continuation.getValue().getSubmitterUserId());
        assertEquals(100L, continuation.getValue().getSupersedesOrderId());
        assertEquals(ORDER_TYPE_CONTINUATION, continuation.getValue().getOrderType());
        verify(agingPoolService).markDealPending(eq(1L), eq(20L), any());
    }

    private void mockEligibleLeadAndOpportunity() {
        LeadDO lead = new LeadDO(); lead.setId(1L); lead.setPersonId(10L); lead.setOwnerUserId(20L); lead.setStatus("converted");
        OpportunityDO opportunity = new OpportunityDO(); opportunity.setId(30L); opportunity.setLeadId(1L); opportunity.setStatus(OPPORTUNITY_STATUS_FOLLOWING);
        when(leadMapper.selectByIdForUpdate(1L, 1L)).thenReturn(lead);
        lenient().when(opportunityMapper.selectByLeadId(1L)).thenReturn(opportunity);
        AreaRespDTO province = new AreaRespDTO();
        province.setId(120000); province.setName("天津市"); province.setType(2); province.setStatus(0); province.setLeafSelectable(true);
        lenient().when(areaApi.getArea(120000)).thenReturn(province);
    }

    private SalesOrderSubmitReqVO request(BigDecimal amount, String mobile, String wechat) {
        SalesOrderSubmitReqVO req = new SalesOrderSubmitReqVO();
        req.setStudentName("测试学员"); req.setStudentNature("new_student"); req.setStudentMobile(mobile); req.setStudentWechatId(wechat);
        req.setProvinceCode("120000"); req.setProvinceName("天津市"); req.setCityCode("OTHER"); req.setCityName("");
        req.setServicePeriod("one_year"); req.setStudentSource("direct_enrollment"); req.setCustomerPaidAt(LocalDateTime.now());
        req.setFeeMode("retail"); req.setPaymentMethod("company_qr"); req.setIdempotencyKey("key-1"); req.setPaymentVouchers(List.of());
        SalesOrderSubmitReqVO.Item item = new SalesOrderSubmitReqVO.Item(); item.setSpuRef("spu-1"); item.setSkuRef("sku-1"); item.setActualAmount(amount);
        req.setItems(List.of(item)); return req;
    }

    private LeadProductSnapshot product() {
        return new LeadProductSnapshot("spu-1", "课程一", null, null, List.of(), null, null, null, null,
                "sku-1", "班型一", "{}", new BigDecimal("99.00"), false, false);
    }
}
