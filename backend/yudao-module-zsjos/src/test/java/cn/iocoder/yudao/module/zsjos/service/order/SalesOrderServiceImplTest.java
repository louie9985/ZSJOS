package cn.iocoder.yudao.module.zsjos.service.order;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.bpm.api.task.BpmProcessInstanceApi;
import cn.iocoder.yudao.module.bpm.api.task.BpmProcessTaskApi;
import cn.iocoder.yudao.module.infra.api.file.FileApi;
import cn.iocoder.yudao.module.infra.api.file.dto.FileInfoRespDTO;
import cn.iocoder.yudao.module.system.api.dict.DictDataApi;
import cn.iocoder.yudao.module.system.api.ip.AreaApi;
import cn.iocoder.yudao.module.system.api.dept.DeptApi;
import cn.iocoder.yudao.module.system.api.notify.NotifyBusinessEventApi;
import cn.iocoder.yudao.module.system.api.ip.dto.AreaRespDTO;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import cn.iocoder.yudao.module.system.api.user.dto.AdminUserRespDTO;
import cn.iocoder.yudao.module.zsjos.controller.admin.order.vo.SalesOrderSubmitReqVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.order.vo.SalesOrderDecisionReqVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.order.vo.SalesOrderMyPageReqVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.order.vo.SalesOrderPageReqVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.order.vo.SalesOrderRepurchaseReqVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.order.vo.SalesOrderTerminateReqVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.order.vo.SalesOrderTeamPageReqVO;
import cn.iocoder.yudao.module.bpm.api.task.dto.BpmTaskPageReqDTO;
import cn.iocoder.yudao.module.bpm.api.task.dto.BpmTaskRespDTO;
import cn.iocoder.yudao.module.bpm.api.task.dto.BpmProcessNodeStatusRespDTO;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.LeadAppealDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.LeadDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.OpportunityDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.PersonDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.order.*;
import cn.iocoder.yudao.module.zsjos.dal.mysql.lead.LeadMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.lead.LeadAppealMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.lead.OpportunityMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.lead.PersonMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.lead.PartnerMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.order.*;
import cn.iocoder.yudao.module.zsjos.service.lead.product.LeadProductSnapshot;
import cn.iocoder.yudao.module.zsjos.service.product.ZsjosProductSkuService;
import cn.iocoder.yudao.module.zsjos.service.lead.LeadLifecycleTaskService;
import cn.iocoder.yudao.module.zsjos.service.lead.LeadInboxFilterConfigService;
import cn.iocoder.yudao.module.zsjos.service.lead.LeadAgingPoolService;
import cn.iocoder.yudao.module.zsjos.service.lead.LeadCollaborationService;
import cn.iocoder.yudao.module.zsjos.service.lead.PersonIdentityWriteService;
import cn.iocoder.yudao.module.zsjos.service.advancedfilter.AdvancedFilterService;
import cn.iocoder.yudao.module.zsjos.service.cashback.CashbackService;
import cn.iocoder.yudao.module.zsjos.service.task.BusinessTaskCommandService;
import cn.iocoder.yudao.module.zsjos.service.task.BusinessTaskCreateCommand;
import cn.iocoder.yudao.module.zsjos.framework.permission.ZsjosPermission;
import cn.iocoder.yudao.module.zsjos.service.registration.RegistrationChecklistConfigService;
import cn.iocoder.yudao.module.zsjos.service.registration.RegistrationService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import static cn.iocoder.yudao.module.bpm.enums.task.BpmProcessInstanceStatusEnum.APPROVE;
import static cn.iocoder.yudao.module.bpm.enums.task.BpmProcessInstanceStatusEnum.CANCEL;
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
    @Mock private RegistrationChecklistConfigService registrationChecklistConfigService;
    @Mock private RegistrationService registrationService;
    @Mock private SalesOrderItemMapper itemMapper;
    @Mock private SalesOrderApprovalRoundMapper roundMapper;
    @Mock private SalesOrderApprovalConfigMapper configMapper;
    @Mock private LeadMapper leadMapper;
    @Mock private LeadAppealMapper leadAppealMapper;
    @Mock private OpportunityMapper opportunityMapper;
    @Mock private PersonMapper personMapper;
    @Mock private PartnerMapper partnerMapper;
    @Mock private ZsjosProductSkuService skuService;
    @Mock private SalesOrderObjectPermissionService permissionService;
    @Mock private FileApi fileApi;
    @Mock private AreaApi areaApi;
    @Mock private DictDataApi dictDataApi;
    @Mock private BpmProcessInstanceApi processInstanceApi;
    @Mock private BpmProcessTaskApi processTaskApi;
    @Mock private LeadLifecycleTaskService lifecycleTaskService;
    @Mock private NotifyBusinessEventApi notifyBusinessEventApi;
    @Mock private AdminUserApi adminUserApi;
    @Mock private DeptApi deptApi;
    @Mock private LeadInboxFilterConfigService inboxFilterConfigService;
    @Mock private LeadAgingPoolService agingPoolService;
    @Mock private LeadCollaborationService collaborationService;
    @Mock private PersonIdentityWriteService personIdentityWriteService;
    @Mock private SalesOrderCommandService commandService;
    @Mock private AdvancedFilterService advancedFilterService;
    @Mock private SalesOrderSupervisorConfirmationService supervisorConfirmationService;
    @Mock private SalesOrderNumberService orderNumberService;
    @Mock private CashbackService cashbackService;
    @Mock private BusinessTaskCommandService businessTaskCommandService;

    @BeforeEach void setUp() {
        TenantContextHolder.setTenantId(1L);
        lenient().when(advancedFilterService.matchOrderIds(any())).thenReturn(null);
        lenient().doNothing().when(agingPoolService).requireCanOperateForUpdate(anyLong(), anyLong(), anyLong());
        lenient().doNothing().when(collaborationService).requireCanEnterDealForUpdate(any(LeadDO.class), anyLong());
        lenient().when(commandService.fingerprint(any())).thenReturn("fingerprint");
        lenient().when(orderNumberService.next()).thenReturn("OD202608141200000001");
        lenient().when(cashbackService.isEligibleDealLead(anyLong())).thenReturn(false);
        lenient().when(fileApi.getFileInfo(1L)).thenReturn(new FileInfoRespDTO(
                1L, 1L, "voucher.pdf", "zsjos/sales-order-voucher/voucher.pdf",
                "https://example.test/voucher.pdf", "application/pdf", 100L, "20"));
    }
    @AfterEach void tearDown() { TenantContextHolder.clear(); }

    @Test
    void financeCourseSummaryFallsBackWhenHistoricalSnapshotIsMalformed() {
        SalesOrderItemDO item = new SalesOrderItemDO();
        item.setProductSnapshot("{malformed");
        item.setProductRef("course-legacy");
        item.setSkuRef("sku-legacy");

        String summary = ReflectionTestUtils.invokeMethod(service, "courseSummary", item);

        assertEquals("course-legacy / sku-legacy", summary);
    }

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
                        && req.getStartUserSelectAssignees().get(TASK_FINANCE).size() == 1
                        && "KZ202608160000000001".equals(req.getVariables().get("leadNo"))));
    }

    @Test
    void createRejectsWhenAnApprovalCenterHasNoEnabledUsers() {
        mockEligibleLeadAndOpportunity();
        SalesOrderApprovalConfigDO config = new SalesOrderApprovalConfigDO();
        config.setRegistrationDeptId(1030L); config.setFinanceDeptId(1040L);
        when(configMapper.selectCurrent()).thenReturn(config);
        when(permissionService.enabledUsers(1030L)).thenReturn(Set.of(301L));
        when(permissionService.enabledUsers(1040L)).thenReturn(Set.of());
        when(skuService.validateLeadProduct("spu-1", false, "sku-1", false)).thenReturn(product());
        doAnswer(invocation -> { ((SalesOrderDO) invocation.getArgument(0)).setId(100L); return 1; })
                .when(orderMapper).insert(any(SalesOrderDO.class));

        ServiceException error = assertThrows(ServiceException.class,
                () -> service.createAndSubmit(1L, 20L, request(BigDecimal.ZERO, "13800138000", null)));

        assertEquals(SALES_ORDER_APPROVAL_CONFIG_INVALID.getCode(), error.getCode());
        verify(processInstanceApi, never()).createProcessInstance(anyLong(), any());
    }

    @Test
    void createRejectsAnyAmountWithoutVoucher() {
        mockEligibleLeadAndOpportunity();
        when(skuService.validateLeadProduct("spu-1", false, "sku-1", false)).thenReturn(product());
        SalesOrderSubmitReqVO request = request(new BigDecimal("10.00"), "13800138000", null);
        request.setPaymentVouchers(List.of());

        ServiceException error = assertThrows(ServiceException.class,
                () -> service.createAndSubmit(1L, 20L, request));

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
    void createRejectsOpportunityFromAnotherCustomer() {
        mockEligibleLeadAndOpportunity();
        OpportunityDO mismatched = new OpportunityDO();
        mismatched.setId(30L); mismatched.setLeadId(1L); mismatched.setPersonId(99L);
        mismatched.setOwnerUserId(20L); mismatched.setStatus(OPPORTUNITY_STATUS_FOLLOWING);
        when(opportunityMapper.selectByLeadIdForUpdate(1L, 1L)).thenReturn(mismatched);

        ServiceException error = assertThrows(ServiceException.class,
                () -> service.createAndSubmit(1L, 20L, request(BigDecimal.ZERO, "13800138000", null)));

        assertEquals(SALES_ORDER_ENTRY_FORBIDDEN.getCode(), error.getCode());
        verify(orderMapper, never()).insert(any(SalesOrderDO.class));
    }

    @Test
    void approvedProcessMakesOrderEffectiveAndLeadAndOpportunityWon() {
        SalesOrderApprovalRoundDO round = new SalesOrderApprovalRoundDO();
        round.setId(200L); round.setOrderId(100L); round.setStatus(ROUND_PENDING); round.setProcessInstanceId("process-1");
        SalesOrderDO order = new SalesOrderDO();
        order.setId(100L); order.setLeadId(1L); order.setOpportunityId(30L); order.setCurrentApprovalRoundId(200L); order.setStatus(STATUS_PENDING_APPROVAL);
        OpportunityDO opportunity = new OpportunityDO(); opportunity.setId(30L); opportunity.setStatus(OPPORTUNITY_STATUS_DEAL_PENDING_APPROVAL);
        LeadDO lead = new LeadDO(); lead.setId(1L); lead.setStatus(STATUS_VALID);
        when(roundMapper.selectByProcessInstanceId("process-1")).thenReturn(round);
        when(roundMapper.selectByIdForUpdate(200L, 1L)).thenReturn(round);
        when(orderMapper.selectByIdForUpdate(100L, 1L)).thenReturn(order);
        when(opportunityMapper.selectById(30L)).thenReturn(opportunity);
        when(leadMapper.selectById(1L)).thenReturn(lead);

        service.handleProcessResult("process-1", APPROVE.getStatus(), null);

        assertEquals(STATUS_EFFECTIVE, order.getStatus()); assertNotNull(order.getEffectiveAt());
        assertEquals(ROUND_APPROVED, round.getStatus()); assertEquals(OPPORTUNITY_STATUS_WON, opportunity.getStatus());
        assertEquals(STATUS_WON, lead.getStatus());
        verify(orderMapper).updateById(order); verify(roundMapper).updateById(round); verify(opportunityMapper).updateById(opportunity);
        verify(leadMapper).updateById(lead);
        verify(lifecycleTaskService).cancelFollowUpReminders(eq(order.getLeadId()), any(), eq("成交订单已生效"));
    }

    @Test
    void rejectedProcessReturnsOriginalOrderForRevision() {
        SalesOrderApprovalRoundDO round = new SalesOrderApprovalRoundDO();
        round.setId(200L); round.setOrderId(100L); round.setStatus(ROUND_PENDING); round.setProcessInstanceId("process-1");
        round.setSubmittedByUserId(20L);
        SalesOrderDO order = new SalesOrderDO();
        order.setId(100L); order.setOrderNo("SO-100"); order.setStudentName("测试学员");
        order.setOpportunityId(30L); order.setCurrentApprovalRoundId(200L); order.setStatus(STATUS_PENDING_APPROVAL);
        OpportunityDO opportunity = new OpportunityDO(); opportunity.setId(30L); opportunity.setStatus(OPPORTUNITY_STATUS_DEAL_PENDING_APPROVAL);
        when(roundMapper.selectByProcessInstanceId("process-1")).thenReturn(round);
        when(roundMapper.selectByIdForUpdate(200L, 1L)).thenReturn(round);
        when(orderMapper.selectByIdForUpdate(100L, 1L)).thenReturn(order);
        when(opportunityMapper.selectById(30L)).thenReturn(opportunity);

        service.handleProcessResult("process-1", REJECT.getStatus(), "资料需补正");

        assertEquals(STATUS_REVISION_REQUIRED, order.getStatus());
        assertEquals(ROUND_REJECTED, round.getStatus());
        assertEquals("资料需补正", round.getDecisionReason());
        assertEquals(OPPORTUNITY_STATUS_FOLLOWING, opportunity.getStatus());
        ArgumentCaptor<BusinessTaskCreateCommand> task = ArgumentCaptor.forClass(BusinessTaskCreateCommand.class);
        verify(businessTaskCommandService).create(task.capture());
        assertEquals(TASK_TYPE_REVISION, task.getValue().taskType());
        assertEquals(20L, task.getValue().assigneeId());
        assertNull(task.getValue().dueAt());
        assertEquals(TASK_ACTION_REVISION, task.getValue().actionCode());
        assertEquals(TASK_REVISION_KEY_PREFIX + 200L, task.getValue().idempotencyKey());
    }

    @Test
    void cancelledProcessDoesNotCreateRevisionTask() {
        SalesOrderApprovalRoundDO round = new SalesOrderApprovalRoundDO();
        round.setId(200L); round.setOrderId(100L); round.setStatus(ROUND_PENDING); round.setProcessInstanceId("process-1");
        round.setSubmittedByUserId(20L);
        SalesOrderDO order = new SalesOrderDO();
        order.setId(100L); order.setCurrentApprovalRoundId(200L); order.setStatus(STATUS_PENDING_APPROVAL);
        when(roundMapper.selectByProcessInstanceId("process-1")).thenReturn(round);
        when(roundMapper.selectByIdForUpdate(200L, 1L)).thenReturn(round);
        when(orderMapper.selectByIdForUpdate(100L, 1L)).thenReturn(order);

        service.handleProcessResult("process-1", CANCEL.getStatus(), "流程异常取消");

        assertEquals(STATUS_REVISION_REQUIRED, order.getStatus());
        verify(businessTaskCommandService, never()).create(any());
    }

    @Test
    void createReturnsExistingOrderForSameIdempotencyIntent() {
        SalesOrderDO existing = new SalesOrderDO();
        existing.setId(100L); existing.setLeadId(1L); existing.setSubmitterUserId(20L);
        when(orderMapper.selectByIdempotencyKey("key-1")).thenReturn(existing);

        Long id = service.createAndSubmit(1L, 20L, request(BigDecimal.ZERO, "13800138000", null));

        assertEquals(100L, id);
        verifyNoInteractions(leadMapper, opportunityMapper, skuService, processInstanceApi);
    }

    @Test
    void repurchaseReplayRejectsSameKeyFromDifferentCenter() {
        SalesOrderDO existing = repurchaseReplay("fingerprint", SUBMITTER_CENTER_SALES);
        when(orderMapper.selectByIdempotencyKey("key-1")).thenReturn(existing);

        ServiceException error = assertThrows(ServiceException.class, () -> ReflectionTestUtils.invokeMethod(service,
                "findIdempotentCustomerOrder", 10L, 20L, "key-1", SUBMITTER_CENTER_STUDENT_DELIVERY,
                "fingerprint"));

        assertEquals(SALES_ORDER_IDEMPOTENCY_CONFLICT.getCode(), error.getCode());
    }

    @Test
    void repurchaseReplayRejectsSameKeyForDifferentRequestFingerprint() {
        SalesOrderDO existing = repurchaseReplay("old-fingerprint", SUBMITTER_CENTER_STUDENT_DELIVERY);
        when(orderMapper.selectByIdempotencyKey("key-1")).thenReturn(existing);

        ServiceException error = assertThrows(ServiceException.class, () -> ReflectionTestUtils.invokeMethod(service,
                "findIdempotentCustomerOrder", 10L, 20L, "key-1", SUBMITTER_CENTER_STUDENT_DELIVERY,
                "new-fingerprint"));

        assertEquals(SALES_ORDER_IDEMPOTENCY_CONFLICT.getCode(), error.getCode());
    }

    @Test
    void createRechecksIdempotencyAfterLeadLock() {
        LeadDO lead = new LeadDO(); lead.setId(1L); lead.setOwnerUserId(20L); lead.setStatus("valid");
        SalesOrderDO existing = new SalesOrderDO();
        existing.setId(100L); existing.setLeadId(1L); existing.setSubmitterUserId(20L);
        when(orderMapper.selectByIdempotencyKey("key-1")).thenReturn(null, existing);
        when(leadMapper.selectByIdForUpdate(1L, 1L)).thenReturn(lead);

        Long id = service.createAndSubmit(1L, 20L, request(BigDecimal.ZERO, "13800138000", null));

        assertEquals(100L, id);
        verify(orderMapper, times(2)).selectByIdempotencyKey("key-1");
        verify(orderMapper, never()).selectActiveByLeadId(anyLong(), any());
        verify(orderMapper, never()).insert(any(SalesOrderDO.class));
    }

    @Test
    void myPageUsesSubmitterScopeAndProjectsVisibleBusinessFields() {
        SalesOrderMyPageReqVO reqVO = new SalesOrderMyPageReqVO();
        reqVO.setPageNo(1); reqVO.setPageSize(20); reqVO.setStatus(STATUS_PENDING_APPROVAL); reqVO.setKeyword("测试");
        SalesOrderDO order = new SalesOrderDO();
        order.setId(100L); order.setCurrentApprovalRoundId(200L); order.setOrderNo("SO-100"); order.setLeadId(1L);
        order.setStatus(STATUS_PENDING_APPROVAL); order.setStudentName("测试学员"); order.setTotalAmount(BigDecimal.TEN);
        SalesOrderApprovalRoundDO round = new SalesOrderApprovalRoundDO(); round.setId(200L); round.setRoundNo(2);
        round.setOrderSnapshot(JsonUtils.toJsonString(Map.of(
                "orderLabels", Map.of("studentNature", "在职", "servicePeriod", "一年"),
                "leadProfile", Map.of("leadNo", "KZ-100", "sourceLabel", "销售自拓录"))));
        SalesOrderItemDO item = new SalesOrderItemDO(); item.setOrderId(100L);
        item.setProductRef("course-1"); item.setSkuRef("sku-1"); item.setProductSnapshot("{malformed");
        when(orderMapper.selectMyPage(20L, reqVO, null)).thenReturn(new PageResult<>(List.of(order), 1L));
        when(roundMapper.selectBatchIds(List.of(200L))).thenReturn(List.of(round));
        when(itemMapper.selectListByOrderIds(List.of(100L))).thenReturn(List.of(item));

        var result = service.getMyPage(reqVO, 20L);

        assertEquals(1L, result.getTotal()); assertEquals(1, result.getList().size());
        assertEquals("SO-100", result.getList().getFirst().getOrderNo());
        assertEquals(2, result.getList().getFirst().getApprovalRoundNo());
        assertEquals("在职", result.getList().getFirst().getStudentNatureLabelSnapshot());
        assertEquals("一年", result.getList().getFirst().getServicePeriodLabelSnapshot());
        assertEquals("KZ-100", result.getList().getFirst().getLeadNo());
        assertEquals("销售自拓录", result.getList().getFirst().getLeadSourceLabel());
        assertEquals("course-1 / sku-1", result.getList().getFirst().getProductSummary());
        verify(orderMapper).selectMyPage(20L, reqVO, null);
        verify(itemMapper).selectListByOrderIds(List.of(100L));
        verifyNoInteractions(fileApi);
    }

    @Test
    void teamPageUsesResolvedDepartmentMembersAndBatchLoadsVisibleItems() {
        SalesOrderTeamPageReqVO reqVO = new SalesOrderTeamPageReqVO();
        reqVO.setPageNo(1); reqVO.setPageSize(20); reqVO.setStatus(STATUS_PENDING_APPROVAL);
        SalesOrderDO order = new SalesOrderDO();
        order.setId(100L); order.setCurrentApprovalRoundId(200L); order.setOrderNo("SO-100");
        order.setStatus(STATUS_PENDING_APPROVAL); order.setStudentName("测试学员"); order.setTotalAmount(BigDecimal.TEN);
        SalesOrderApprovalRoundDO round = new SalesOrderApprovalRoundDO(); round.setId(200L); round.setRoundNo(2);
        Set<Long> teamUserIds = Set.of(20L, 21L);
        when(permissionService.teamUserIds(30L)).thenReturn(teamUserIds);
        when(orderMapper.selectTeamPage(teamUserIds, reqVO, null)).thenReturn(new PageResult<>(List.of(order), 1L));
        when(roundMapper.selectBatchIds(List.of(200L))).thenReturn(List.of(round));
        when(itemMapper.selectListByOrderIds(List.of(100L))).thenReturn(List.of());

        var result = service.getTeamPage(reqVO, 30L);

        assertEquals(1L, result.getTotal()); assertEquals("SO-100", result.getList().getFirst().getOrderNo());
        verify(orderMapper).selectTeamPage(teamUserIds, reqVO, null);
        verify(itemMapper).selectListByOrderIds(List.of(100L));
        verifyNoInteractions(fileApi);
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
    void getProjectsReviewerIdentityResultAndTimeFromBpmHistory() {
        SalesOrderDO order = new SalesOrderDO();
        order.setId(100L); order.setOrderNo("SO-100"); order.setLeadId(1L); order.setStatus(STATUS_PENDING_APPROVAL);
        order.setStudentName("测试学员"); order.setTotalAmount(BigDecimal.ZERO); order.setSubmitterUserId(20L);
        SalesOrderApprovalRoundDO round = new SalesOrderApprovalRoundDO();
        round.setId(200L); round.setOrderId(100L); round.setRoundNo(1); round.setProcessInstanceId("process-1");
        BpmProcessNodeStatusRespDTO registration = new BpmProcessNodeStatusRespDTO();
        registration.setTaskDefinitionKey(TASK_REGISTRATION); registration.setStatus("approved");
        registration.setReviewerUserId(233L); registration.setReviewerUserName("审核员甲");
        registration.setEndTime(LocalDateTime.of(2026, 8, 12, 10, 30));
        when(orderMapper.selectById(100L)).thenReturn(order);
        when(roundMapper.selectLatestByOrderId(100L)).thenReturn(round);
        when(itemMapper.selectListByOrderId(100L)).thenReturn(List.of());
        when(processTaskApi.getProcessNodeStatuses("process-1", Set.of(TASK_REGISTRATION, TASK_FINANCE)))
                .thenReturn(List.of(registration));

        var result = service.get(100L, 20L);

        assertEquals("approved", result.getRegistrationApproval().getStatus());
        assertEquals(233L, result.getRegistrationApproval().getReviewerUserId());
        assertEquals("审核员甲", result.getRegistrationApproval().getReviewerUserName());
        assertEquals(LocalDateTime.of(2026, 8, 12, 10, 30), result.getRegistrationApproval().getEndTime());
    }

    @Test
    void getProjectsAuthoritativeLinkedLeadProfile() {
        SalesOrderDO order = new SalesOrderDO();
        order.setId(100L); order.setOrderNo("SO-100"); order.setLeadId(1L); order.setStatus(STATUS_EFFECTIVE);
        order.setStudentName("订单学员"); order.setTotalAmount(BigDecimal.ZERO); order.setSubmitterUserId(20L);
        LeadDO lead = new LeadDO();
        lead.setId(1L); lead.setLeadNo("KZ202608191041490002"); lead.setSubmittedName("客资客户");
        lead.setSubmittedMobile("19926231001"); lead.setSubmittedWechatId("wx-customer");
        lead.setSourceType(SOURCE_INTERNAL_NEW_MEDIA); lead.setSourceUserId(31L); lead.setOwnerUserId(32L);
        lead.setSourceChannelId("information_flow"); lead.setLeadCategory("high_intent"); lead.setDispatchMode("auto");
        lead.setProvinceName("广东省"); lead.setCityName("湛江市");
        AdminUserRespDTO sourceUser = new AdminUserRespDTO(); sourceUser.setId(31L); sourceUser.setNickname("新媒体专员");
        AdminUserRespDTO ownerUser = new AdminUserRespDTO(); ownerUser.setId(32L); ownerUser.setNickname("销售专员2");
        when(orderMapper.selectById(100L)).thenReturn(order);
        when(leadMapper.selectById(1L)).thenReturn(lead);
        when(itemMapper.selectListByOrderId(100L)).thenReturn(List.of());
        when(adminUserApi.getUserMap(Set.of(31L, 32L))).thenReturn(Map.of(31L, sourceUser, 32L, ownerUser));

        var result = service.get(100L, 40L);

        assertNotNull(result.getLeadProfile());
        assertEquals("KZ202608191041490002", result.getLeadProfile().getLeadNo());
        assertEquals("客资客户", result.getLeadProfile().getSubmittedName());
        assertEquals("新媒体提交", result.getLeadProfile().getSourceLabel());
        assertEquals("新媒体专员", result.getLeadProfile().getSourceUserName());
        assertEquals("销售专员2", result.getLeadProfile().getOwnerUserName());
        assertEquals("information_flow", result.getLeadProfile().getSourceChannel());
        assertEquals("high_intent", result.getLeadProfile().getLeadCategory());
    }

    @Test
    void getOmitsLeadProfileForUnlinkedRepurchase() {
        SalesOrderDO order = new SalesOrderDO();
        order.setId(100L); order.setOrderNo("SO-100"); order.setStatus(STATUS_EFFECTIVE);
        order.setOrderType(ORDER_TYPE_REPURCHASE); order.setStudentName("复购学员");
        order.setTotalAmount(BigDecimal.ZERO); order.setSubmitterUserId(20L);
        when(orderMapper.selectById(100L)).thenReturn(order);
        when(itemMapper.selectListByOrderId(100L)).thenReturn(List.of());

        var result = service.get(100L, 20L);

        assertNull(result.getLeadProfile());
        verifyNoInteractions(adminUserApi, partnerMapper);
    }

    @Test
    void getPrefersApprovalRoundLabelSnapshotsOverCurrentDictionaryProjection() {
        SalesOrderDO order = new SalesOrderDO();
        order.setId(100L); order.setOrderNo("SO-100"); order.setLeadId(1L); order.setStatus(STATUS_PENDING_APPROVAL);
        order.setStudentName("当前名称"); order.setStudentNature("adult"); order.setServicePeriod("one_year");
        order.setStudentSource("lead"); order.setFeeMode("full"); order.setPaymentMethod("wechat");
        SalesOrderApprovalRoundDO round = new SalesOrderApprovalRoundDO();
        round.setId(200L); round.setOrderId(100L); round.setRoundNo(1); round.setProcessInstanceId("process-1");
        round.setOrderSnapshot(JsonUtils.toJsonString(Map.of(
                "snapshotVersion", 1,
                "orderLabels", Map.of("studentNature", "录入时性质", "servicePeriod", "录入时周期",
                        "studentSource", "录入时来源", "feeMode", "录入时缴费方式", "paymentMethod", "录入时支付方式"),
                "leadProfile", Map.of("leadNo", "KZ202608191234560001", "submittedName", "录入时客户",
                        "leadCategory", "a", "leadCategoryLabelSnapshot", "录入时分类",
                        "sourceChannel", "b", "sourceChannelLabelSnapshot", "录入时渠道"))));
        when(orderMapper.selectById(100L)).thenReturn(order);
        when(roundMapper.selectLatestByOrderId(100L)).thenReturn(round);
        when(itemMapper.selectListByOrderId(100L)).thenReturn(List.of());
        when(processTaskApi.getProcessNodeStatuses("process-1", Set.of(TASK_REGISTRATION, TASK_FINANCE))).thenReturn(List.of());

        var result = service.get(100L, 20L);

        assertEquals("录入时性质", result.getStudentNatureLabelSnapshot());
        assertEquals("录入时支付方式", result.getPaymentMethodLabelSnapshot());
        assertEquals("录入时分类", result.getLeadProfile().getLeadCategoryLabelSnapshot());
        assertEquals("录入时渠道", result.getLeadProfile().getSourceChannelLabelSnapshot());
        assertEquals("KZ202608191234560001", result.getLeadProfile().getLeadNo());
    }

    @Test
    void approvalInboxRestrictsSingleCenterUserToRegistrationTasks() {
        SalesOrderPageReqVO reqVO = new SalesOrderPageReqVO();
        reqVO.setPageNo(1); reqVO.setPageSize(20); reqVO.setHandled(false); reqVO.setCenter(CENTER_REGISTRATION);
        when(permissionService.approvalTaskKeys(20L)).thenReturn(Set.of(TASK_REGISTRATION));
        when(processTaskApi.getTodoTaskPage(eq(20L), any())).thenReturn(PageResult.empty());

        PageResult<?> result = service.getInboxPage(reqVO, 20L);

        assertTrue(result.getList().isEmpty());
        ArgumentCaptor<BpmTaskPageReqDTO> captor = ArgumentCaptor.forClass(BpmTaskPageReqDTO.class);
        verify(processTaskApi, atLeastOnce()).getTodoTaskPage(eq(20L), captor.capture());
        assertTrue(captor.getAllValues().stream().allMatch(value -> TASK_REGISTRATION.equals(value.getTaskDefinitionKey())));
    }

    @Test
    void approvalInboxRejectsCenterOutsideUserScope() {
        SalesOrderPageReqVO reqVO = new SalesOrderPageReqVO();
        reqVO.setPageNo(1); reqVO.setPageSize(20); reqVO.setCenter(CENTER_FINANCE);
        when(permissionService.approvalTaskKeys(20L)).thenReturn(Set.of(TASK_REGISTRATION));

        ServiceException error = assertThrows(ServiceException.class, () -> service.getInboxPage(reqVO, 20L));

        assertEquals(SALES_ORDER_PERMISSION_DENIED.getCode(), error.getCode());
        verifyNoInteractions(processTaskApi);
    }

    @Test
    void approveRemainsAvailableWhileSupervisorParallelSignIsPending() {
        SalesOrderDecisionReqVO request = mockPendingFinanceDecision("approve-1");

        service.approve(100L, 20L, request);

        verify(supervisorConfirmationService, never()).cancelPending(eq(200L), eq(TASK_FINANCE), any(LocalDateTime.class));
        verify(processTaskApi).approveTask(eq(20L), argThat(decision -> "finance-task".equals(decision.getTaskId())));
    }

    @Test
    void rejectRemainsAvailableWhileSupervisorParallelSignIsPending() {
        SalesOrderDecisionReqVO request = mockPendingFinanceDecision("reject-1");

        service.reject(100L, 20L, request);

        verify(supervisorConfirmationService).cancelPending(eq(200L), eq(TASK_FINANCE), any(LocalDateTime.class));
        verify(processTaskApi).rejectTask(eq(20L), argThat(decision -> "finance-task".equals(decision.getTaskId())));
    }

    @Test
    void reviseCreatesIndependentSuccessorAndPreservesRejectedOrder() {
        SalesOrderDO order = new SalesOrderDO();
        order.setId(100L); order.setLeadId(1L); order.setOpportunityId(30L); order.setStatus(STATUS_REVISION_REQUIRED);
        when(orderMapper.selectByIdForUpdate(100L, 1L)).thenReturn(order);
        mockEligibleLeadAndOpportunity();
        SalesOrderApprovalRoundDO previous = new SalesOrderApprovalRoundDO(); previous.setId(200L); previous.setRoundNo(1);
        when(roundMapper.selectLatestByOrderId(100L)).thenReturn(previous);
        SalesOrderApprovalConfigDO config = new SalesOrderApprovalConfigDO();
        config.setRegistrationDeptId(1030L); config.setFinanceDeptId(1040L);
        when(configMapper.selectCurrent()).thenReturn(config);
        when(permissionService.enabledUsers(1030L)).thenReturn(Set.of(301L));
        when(permissionService.enabledUsers(1040L)).thenReturn(Set.of(401L));
        when(processInstanceApi.createProcessInstance(eq(20L), any())).thenReturn("process-2");
        when(skuService.validateLeadProduct("spu-1", false, "sku-1", false)).thenReturn(product());
        doAnswer(invocation -> { SalesOrderDO inserted = invocation.getArgument(0); inserted.setId(101L); return 1; })
                .when(orderMapper).insert(any(SalesOrderDO.class));
        doAnswer(invocation -> { ((SalesOrderApprovalRoundDO) invocation.getArgument(0)).setId(201L); return 1; })
                .when(roundMapper).insert(any(SalesOrderApprovalRoundDO.class));

        Long successorId = service.reviseAndResubmit(100L, 20L, request(BigDecimal.ZERO, "13800138000", null));

        assertEquals(101L, successorId);
        assertEquals(STATUS_SUPERSEDED, order.getStatus());
        assertEquals(101L, order.getSupersededByOrderId());
        verify(itemMapper, never()).deleteByOrderId(100L);
        verify(orderMapper).insert(org.mockito.Mockito.<SalesOrderDO>argThat(inserted -> Objects.equals(inserted.getId(), 101L)
                && Objects.equals(inserted.getSupersedesOrderId(), 100L)
                && STATUS_PENDING_APPROVAL.equals(inserted.getStatus())));
        verify(roundMapper).insert(org.mockito.Mockito.<SalesOrderApprovalRoundDO>argThat(round -> round.getOrderId().equals(101L)
                && round.getRoundNo() == 1 && "process-2".equals(round.getProcessInstanceId())
                && round.getOrderSnapshot() != null));
        verify(registrationService).cancelByOrderId(eq(100L), eq("原订单已被接续"), any());
        verify(supervisorConfirmationService).cancelPending(eq(200L), any());
        verify(businessTaskCommandService).completeByKey(eq(TASK_REVISION_KEY_PREFIX + 200L), any());
    }

    @Test
    void reviseRechecksIdempotencyAfterLockAndReturnsConcurrentSuccessor() {
        SalesOrderDO order = new SalesOrderDO();
        order.setId(100L); order.setStatus(STATUS_REVISION_REQUIRED);
        SalesOrderDO successor = new SalesOrderDO();
        successor.setId(101L); successor.setSupersedesOrderId(100L);
        SalesOrderApprovalRoundDO successorRound = new SalesOrderApprovalRoundDO();
        successorRound.setOrderId(101L);
        when(roundMapper.selectByIdempotencyKey("key-1")).thenReturn(null, successorRound);
        when(orderMapper.selectByIdForUpdate(100L, 1L)).thenReturn(order);
        when(orderMapper.selectById(101L)).thenReturn(successor);

        Long successorId = service.reviseAndResubmit(100L, 20L,
                request(BigDecimal.ZERO, "13800138000", null));

        assertEquals(101L, successorId);
        verify(orderMapper, never()).updateById(any(SalesOrderDO.class));
        verify(orderMapper, never()).insert(any(SalesOrderDO.class));
        verifyNoInteractions(processInstanceApi);
    }

    @Test
    void customerOrderDetailRequiresSamePerson() {
        LeadDO lead = new LeadDO(); lead.setId(1L); lead.setPersonId(10L);
        SalesOrderDO order = new SalesOrderDO(); order.setId(100L); order.setPersonId(11L);
        when(leadMapper.selectById(1L)).thenReturn(lead);
        when(orderMapper.selectById(100L)).thenReturn(order);

        ServiceException error = assertThrows(ServiceException.class,
                () -> service.getCustomerOrder(1L, 100L, 20L));

        assertEquals(SALES_ORDER_PERMISSION_DENIED.getCode(), error.getCode());
        verifyNoInteractions(itemMapper, processTaskApi);
    }

    @Test
    void customerOrderReadsRequireLeadOwnerPermission() throws NoSuchMethodException {
        ZsjosPermission listPermission = SalesOrderServiceImpl.class
                .getMethod("getCustomerOrders", Long.class, Long.class)
                .getAnnotation(ZsjosPermission.class);
        ZsjosPermission detailPermission = SalesOrderServiceImpl.class
                .getMethod("getCustomerOrder", Long.class, Long.class, Long.class)
                .getAnnotation(ZsjosPermission.class);

        assertNotNull(listPermission);
        assertNotNull(detailPermission);
        assertEquals("sales-history-read", listPermission.action());
        assertEquals("sales-history-read", detailPermission.action());
    }

    @Test
    void terminatedOrderCannotBeResubmittedWhenReplacementOrderIsActive() {
        SalesOrderDO order = new SalesOrderDO();
        order.setId(100L); order.setLeadId(1L); order.setOpportunityId(30L); order.setStatus(STATUS_TERMINATED);
        SalesOrderDO replacement = new SalesOrderDO(); replacement.setId(101L); replacement.setStatus(STATUS_PENDING_APPROVAL);
        when(orderMapper.selectByIdForUpdate(100L, 1L)).thenReturn(order);
        mockEligibleLeadAndOpportunity();
        when(orderMapper.selectOtherActiveByLeadId(1L, 100L, ACTIVE_ORDER_STATUSES)).thenReturn(replacement);

        ServiceException error = assertThrows(ServiceException.class,
                () -> service.reviseAndResubmit(100L, 20L, request(BigDecimal.ZERO, "13800138000", null)));

        assertEquals(SALES_ORDER_ACTIVE_DUPLICATE.getCode(), error.getCode());
        assertEquals(STATUS_TERMINATED, order.getStatus());
        verifyNoInteractions(skuService, processInstanceApi);
    }

    @Test
    void terminatedRepurchaseCannotBeResubmittedWhenAnyCustomerOrderIsActive() {
        SalesOrderDO order = new SalesOrderDO();
        order.setId(100L); order.setPersonId(10L); order.setOrderType(ORDER_TYPE_REPURCHASE);
        order.setStatus(STATUS_TERMINATED);
        SalesOrderDO replacement = new SalesOrderDO(); replacement.setId(101L); replacement.setStatus(STATUS_PENDING_APPROVAL);
        when(orderMapper.selectByIdForUpdate(100L, 1L)).thenReturn(order);
        when(personMapper.selectByIdForUpdate(10L, 1L)).thenReturn(new PersonDO().setId(10L));
        when(orderMapper.selectOtherActiveByPersonId(10L, 100L, ACTIVE_ORDER_STATUSES)).thenReturn(replacement);

        ServiceException error = assertThrows(ServiceException.class,
                () -> service.reviseAndResubmit(100L, 20L, request(BigDecimal.ZERO, "13800138000", null)));

        assertEquals(SALES_ORDER_ACTIVE_DUPLICATE.getCode(), error.getCode());
        assertEquals(STATUS_TERMINATED, order.getStatus());
        verifyNoInteractions(skuService, processInstanceApi);
    }

    @Test
    void systemRepurchasePersistsCustomerOnlyAndStartsDualApproval() {
        LeadDO lead = new LeadDO(); lead.setId(1L); lead.setPersonId(10L); lead.setOwnerUserId(20L); lead.setStatus(STATUS_WON);
        when(leadMapper.selectByIdForUpdate(1L, 1L)).thenReturn(lead);
        when(personMapper.selectByIdForUpdate(10L, 1L)).thenReturn(new PersonDO().setId(10L));
        when(orderMapper.hasEffectiveOrder(10L)).thenReturn(true);
        SalesOrderApprovalConfigDO config = new SalesOrderApprovalConfigDO();
        config.setRegistrationDeptId(1030L); config.setFinanceDeptId(1040L); when(configMapper.selectCurrent()).thenReturn(config);
        when(permissionService.enabledUsers(1030L)).thenReturn(Set.of(301L)); when(permissionService.enabledUsers(1040L)).thenReturn(Set.of(401L));
        when(processInstanceApi.createProcessInstance(eq(20L), any())).thenReturn("process-repurchase");
        when(skuService.validateLeadProduct("spu-1", false, "sku-1", false)).thenReturn(product());
        AreaRespDTO province = new AreaRespDTO(); province.setId(120000); province.setName("天津市");
        province.setType(2); province.setStatus(0); province.setLeafSelectable(true); when(areaApi.getArea(120000)).thenReturn(province);
        doAnswer(invocation -> { ((SalesOrderDO) invocation.getArgument(0)).setId(101L); return 1; }).when(orderMapper).insert(any(SalesOrderDO.class));
        doAnswer(invocation -> { ((SalesOrderApprovalRoundDO) invocation.getArgument(0)).setId(201L); return 1; }).when(roundMapper).insert(any(SalesOrderApprovalRoundDO.class));
        SalesOrderRepurchaseReqVO req = new SalesOrderRepurchaseReqVO(); req.setRepurchaseReason("继续学习");
        req.setOrder(request(BigDecimal.ZERO, "13800138000", null));

        Long id = service.createSystemRepurchase(1L, 20L, req);

        assertEquals(101L, id);
        verify(orderMapper).insert(org.mockito.Mockito.<SalesOrderDO>argThat(order -> ORDER_TYPE_REPURCHASE.equals(order.getOrderType())
                && order.getLeadId() == null && order.getOpportunityId() == null && Objects.equals(order.getPersonId(), 10L)
                && Objects.equals(order.getFormalSalesUserId(), 20L)));
        verifyNoInteractions(opportunityMapper, lifecycleTaskService);
    }

    @Test
    void terminateLocksCurrentRoundAndCancelsBpm() {
        SalesOrderDO order = new SalesOrderDO(); order.setId(100L); order.setOrderType(ORDER_TYPE_REPURCHASE);
        order.setStatus(STATUS_PENDING_APPROVAL); order.setSubmitterUserId(20L); order.setCurrentApprovalRoundId(200L); order.setVersion(3);
        SalesOrderApprovalRoundDO round = new SalesOrderApprovalRoundDO(); round.setId(200L); round.setOrderId(100L);
        round.setProcessInstanceId("process-1"); round.setStatus(ROUND_PENDING); round.setVersion(4);
        when(orderMapper.selectByIdForUpdate(100L, 1L)).thenReturn(order); when(roundMapper.selectByIdForUpdate(200L, 1L)).thenReturn(round);
        SalesOrderTerminateReqVO req = new SalesOrderTerminateReqVO(); req.setApprovalRoundId(200L); req.setOrderVersion(3);
        req.setRoundVersion(4); req.setReason("客户取消"); req.setIdempotencyKey("terminate-1");

        service.terminate(100L, 20L, req);

        assertEquals(STATUS_TERMINATED, order.getStatus()); assertEquals(ROUND_TERMINATED, round.getStatus());
        assertEquals(4, order.getVersion()); assertEquals(5, round.getVersion());
        verify(processInstanceApi).terminateProcessInstanceByBusiness(
                20L, "process-1", "zsjos.sales-order.terminate", "客户取消");
        verifyNoInteractions(opportunityMapper, agingPoolService);
    }

    private void mockEligibleLeadAndOpportunity() {
        LeadDO lead = new LeadDO(); lead.setId(1L); lead.setLeadNo("KZ202608160000000001");
        lead.setPersonId(10L); lead.setOwnerUserId(20L); lead.setStatus("valid");
        OpportunityDO opportunity = new OpportunityDO(); opportunity.setId(30L); opportunity.setLeadId(1L);
        opportunity.setPersonId(10L); opportunity.setOwnerUserId(20L); opportunity.setStatus(OPPORTUNITY_STATUS_FOLLOWING);
        when(leadMapper.selectByIdForUpdate(1L, 1L)).thenReturn(lead);
        lenient().when(leadMapper.selectById(1L)).thenReturn(lead);
        lenient().when(opportunityMapper.selectByLeadIdForUpdate(1L, 1L)).thenReturn(opportunity);
        AreaRespDTO province = new AreaRespDTO();
        province.setId(120000); province.setName("天津市"); province.setType(2); province.setStatus(0); province.setLeafSelectable(true);
        lenient().when(areaApi.getArea(120000)).thenReturn(province);
    }

    private SalesOrderDecisionReqVO mockPendingFinanceDecision(String idempotencyKey) {
        SalesOrderDO order = new SalesOrderDO();
        order.setId(100L); order.setStatus(STATUS_PENDING_APPROVAL); order.setCurrentApprovalRoundId(200L); order.setVersion(3);
        SalesOrderApprovalRoundDO round = new SalesOrderApprovalRoundDO();
        round.setId(200L); round.setOrderId(100L); round.setStatus(ROUND_PENDING);
        round.setProcessInstanceId("process-1"); round.setVersion(4);
        BpmTaskRespDTO task = new BpmTaskRespDTO();
        task.setId("finance-task"); task.setProcessInstanceId("process-1");
        task.setBusinessKey(BUSINESS_KEY_PREFIX + 100L); task.setTaskDefinitionKey(TASK_FINANCE); task.setSignTask(false);
        when(orderMapper.selectByIdForUpdate(100L, 1L)).thenReturn(order);
        when(roundMapper.selectByIdForUpdate(200L, 1L)).thenReturn(round);
        when(processTaskApi.getTodoTask(20L, "finance-task")).thenReturn(task);

        SalesOrderDecisionReqVO request = new SalesOrderDecisionReqVO();
        request.setTaskId("finance-task"); request.setApprovalRoundId(200L);
        request.setOrderVersion(3); request.setRoundVersion(4); request.setReason("审批意见");
        request.setIdempotencyKey(idempotencyKey);
        return request;
    }

    private SalesOrderDO repurchaseReplay(String fingerprint, String submitterCenter) {
        SalesOrderDO order = new SalesOrderDO();
        order.setId(100L); order.setPersonId(10L); order.setSubmitterUserId(20L);
        order.setSubmitterCenterType(submitterCenter); order.setSubmissionRequestFingerprint(fingerprint);
        return order;
    }

    private SalesOrderSubmitReqVO request(BigDecimal amount, String mobile, String wechat) {
        SalesOrderSubmitReqVO req = new SalesOrderSubmitReqVO();
        req.setStudentName("测试学员"); req.setStudentNature("new_student"); req.setStudentMobile(mobile); req.setStudentWechatId(wechat);
        req.setProvinceCode("120000"); req.setProvinceName("天津市"); req.setCityCode("OTHER"); req.setCityName("");
        req.setServicePeriod("one_year"); req.setStudentSource("direct_enrollment"); req.setCustomerPaidAt(LocalDateTime.now());
        req.setFeeMode("retail"); req.setPaymentMethod("company_qr"); req.setIdempotencyKey("key-1");
        SalesOrderSubmitReqVO.Attachment voucher = new SalesOrderSubmitReqVO.Attachment(); voucher.setInfraFileId(1L);
        req.setPaymentVouchers(List.of(voucher));
        SalesOrderSubmitReqVO.Item item = new SalesOrderSubmitReqVO.Item(); item.setSpuRef("spu-1"); item.setSkuRef("sku-1"); item.setActualAmount(amount);
        req.setItems(List.of(item)); return req;
    }

    private LeadProductSnapshot product() {
        return new LeadProductSnapshot("spu-1", "课程一", null, null, List.of(), null, null, null, null,
                "sku-1", "班型一", "{}", new BigDecimal("99.00"), false, false);
    }
}
