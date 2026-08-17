package cn.iocoder.yudao.module.zsjos.service.order;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.bpm.api.task.BpmProcessInstanceApi;
import cn.iocoder.yudao.module.bpm.api.task.BpmProcessTaskApi;
import cn.iocoder.yudao.module.infra.api.file.FileApi;
import cn.iocoder.yudao.module.infra.api.file.dto.FileInfoRespDTO;
import cn.iocoder.yudao.module.system.api.dict.DictDataApi;
import cn.iocoder.yudao.module.system.api.ip.AreaApi;
import cn.iocoder.yudao.module.system.api.dept.DeptApi;
import cn.iocoder.yudao.module.system.api.notify.NotifyBusinessEventApi;
import cn.iocoder.yudao.module.system.api.ip.dto.AreaRespDTO;
import cn.iocoder.yudao.module.zsjos.controller.admin.order.vo.SalesOrderSubmitReqVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.order.vo.SalesOrderMyPageReqVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.order.vo.SalesOrderPageReqVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.order.vo.SalesOrderRepurchaseReqVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.order.vo.SalesOrderTerminateReqVO;
import cn.iocoder.yudao.module.bpm.api.task.dto.BpmTaskPageReqDTO;
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
import cn.iocoder.yudao.module.zsjos.dal.mysql.order.*;
import cn.iocoder.yudao.module.zsjos.service.lead.product.LeadProductSnapshot;
import cn.iocoder.yudao.module.zsjos.service.product.ZsjosProductSkuService;
import cn.iocoder.yudao.module.zsjos.service.lead.LeadLifecycleTaskService;
import cn.iocoder.yudao.module.zsjos.service.lead.LeadInboxFilterConfigService;
import cn.iocoder.yudao.module.zsjos.service.lead.LeadAgingPoolService;
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
    @Mock private LeadInboxFilterConfigService inboxFilterConfigService;
    @Mock private LeadAgingPoolService agingPoolService;
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
        when(opportunityMapper.selectByLeadId(1L)).thenReturn(mismatched);

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
    void myPageUsesSubmitterScopeAndDoesNotLoadHeavyDetails() {
        SalesOrderMyPageReqVO reqVO = new SalesOrderMyPageReqVO();
        reqVO.setPageNo(1); reqVO.setPageSize(20); reqVO.setStatus(STATUS_PENDING_APPROVAL); reqVO.setKeyword("测试");
        SalesOrderDO order = new SalesOrderDO();
        order.setId(100L); order.setCurrentApprovalRoundId(200L); order.setOrderNo("SO-100"); order.setLeadId(1L);
        order.setStatus(STATUS_PENDING_APPROVAL); order.setStudentName("测试学员"); order.setTotalAmount(BigDecimal.TEN);
        SalesOrderApprovalRoundDO round = new SalesOrderApprovalRoundDO(); round.setId(200L); round.setRoundNo(2);
        when(orderMapper.selectMyPage(20L, reqVO, null)).thenReturn(new PageResult<>(List.of(order), 1L));
        when(roundMapper.selectBatchIds(List.of(200L))).thenReturn(List.of(round));

        var result = service.getMyPage(reqVO, 20L);

        assertEquals(1L, result.getTotal()); assertEquals(1, result.getList().size());
        assertEquals("SO-100", result.getList().getFirst().getOrderNo());
        assertEquals(2, result.getList().getFirst().getApprovalRoundNo());
        verify(orderMapper).selectMyPage(20L, reqVO, null);
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
    void reviseKeepsOrderAndStartsNewImmutableRound() {
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
        doAnswer(invocation -> { ((SalesOrderApprovalRoundDO) invocation.getArgument(0)).setId(201L); return 1; })
                .when(roundMapper).insert(any(SalesOrderApprovalRoundDO.class));

        service.reviseAndResubmit(100L, 20L, request(BigDecimal.ZERO, "13800138000", null));

        assertEquals(STATUS_PENDING_APPROVAL, order.getStatus());
        assertEquals(201L, order.getCurrentApprovalRoundId());
        verify(itemMapper).deleteByOrderId(100L);
        verify(roundMapper).insert(org.mockito.Mockito.<SalesOrderApprovalRoundDO>argThat(round -> round.getOrderId().equals(100L)
                && round.getRoundNo() == 2 && "process-2".equals(round.getProcessInstanceId())
                && round.getOrderSnapshot() != null));
        verify(businessTaskCommandService).completeByKey(eq(TASK_REVISION_KEY_PREFIX + 200L), any());
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
        assertEquals("owner-or-manager-read", listPermission.action());
        assertEquals("owner-or-manager-read", detailPermission.action());
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
