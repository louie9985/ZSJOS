package cn.iocoder.yudao.module.zsjos.service.order;

import cn.iocoder.yudao.framework.common.enums.CommonStatusEnum;
import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.bpm.api.task.BpmProcessTaskApi;
import cn.iocoder.yudao.module.bpm.api.task.dto.BpmTaskDecisionReqDTO;
import cn.iocoder.yudao.module.bpm.api.task.dto.BpmTaskRespDTO;
import cn.iocoder.yudao.module.bpm.api.task.dto.BpmTaskSignReqDTO;
import cn.iocoder.yudao.module.system.api.dept.DeptApi;
import cn.iocoder.yudao.module.system.api.dept.dto.DeptRespDTO;
import cn.iocoder.yudao.module.system.api.notify.NotifyBusinessEventApi;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import cn.iocoder.yudao.module.system.api.user.dto.AdminUserRespDTO;
import cn.iocoder.yudao.module.zsjos.controller.admin.order.vo.SalesOrderSupervisorDecisionReqVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.order.vo.SalesOrderSupervisorRequestReqVO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.order.SalesOrderApprovalRoundDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.order.SalesOrderDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.order.SalesOrderSupervisorConfirmationDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.order.SalesOrderApprovalRoundMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.order.SalesOrderMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.order.SalesOrderSupervisorConfirmationMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static cn.iocoder.yudao.module.zsjos.enums.SalesOrderConstants.*;
import static cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SalesOrderSupervisorConfirmationServiceTest {

    private static final Long ORDER_ID = 100L;
    private static final Long ROUND_ID = 200L;
    private static final Long REQUESTER_ID = 20L;
    private static final Long FORMAL_SALES_ID = 21L;
    private static final Long SUPERVISOR_ID = 30L;

    @InjectMocks private SalesOrderSupervisorConfirmationService service;
    @Mock private SalesOrderMapper orderMapper;
    @Mock private SalesOrderApprovalRoundMapper roundMapper;
    @Mock private SalesOrderSupervisorConfirmationMapper confirmationMapper;
    @Mock private SalesOrderCommandService commandService;
    @Mock private BpmProcessTaskApi processTaskApi;
    @Mock private AdminUserApi adminUserApi;
    @Mock private DeptApi deptApi;
    @Mock private NotifyBusinessEventApi notifyBusinessEventApi;
    @Mock private PermissionApi permissionApi;

    private SalesOrderDO order;
    private SalesOrderApprovalRoundDO round;

    @BeforeEach
    void setUp() {
        TenantContextHolder.setTenantId(1L);
        order = new SalesOrderDO();
        order.setId(ORDER_ID); order.setStatus(STATUS_PENDING_APPROVAL); order.setFormalSalesUserId(FORMAL_SALES_ID);
        order.setCurrentApprovalRoundId(ROUND_ID); order.setVersion(3);
        round = new SalesOrderApprovalRoundDO();
        round.setId(ROUND_ID); round.setOrderId(ORDER_ID); round.setStatus(ROUND_PENDING);
        round.setProcessInstanceId("process-1"); round.setVersion(4); round.setSupervisorConfirmationEnabled(true);
        lenient().when(orderMapper.selectByIdForUpdate(ORDER_ID, 1L)).thenReturn(order);
        lenient().when(roundMapper.selectByIdForUpdate(ROUND_ID, 1L)).thenReturn(round);
        lenient().when(commandService.fingerprint(any())).thenReturn("fingerprint");
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    void requestCreatesBeforeSignTaskAndLocksCenter() {
        mockOrdinaryTask();
        mockSupervisor(SUPERVISOR_ID, CommonStatusEnum.ENABLE.getStatus());
        when(processTaskApi.createBeforeSignTask(eq(REQUESTER_ID), any())).thenReturn("supervisor-task-1");
        doAnswer(invocation -> {
            ((SalesOrderSupervisorConfirmationDO) invocation.getArgument(0)).setId(300L);
            return 1;
        }).when(confirmationMapper).insert(any(SalesOrderSupervisorConfirmationDO.class));

        service.request(ORDER_ID, REQUESTER_ID, request());

        ArgumentCaptor<BpmTaskSignReqDTO> signCaptor = ArgumentCaptor.forClass(BpmTaskSignReqDTO.class);
        verify(processTaskApi).createBeforeSignTask(eq(REQUESTER_ID), signCaptor.capture());
        assertEquals("task-1", signCaptor.getValue().getTaskId());
        assertEquals(SUPERVISOR_ID, signCaptor.getValue().getAssigneeUserId());
        verify(confirmationMapper).insert(argThat((SalesOrderSupervisorConfirmationDO row) -> SUPERVISOR_PENDING.equals(row.getStatus())
                && "supervisor-task-1".equals(row.getSupervisorTaskId())
                && TASK_REGISTRATION.equals(row.getTaskDefinitionKey())));
        assertEquals(4, order.getVersion());
        assertEquals(5, round.getVersion());
        verify(commandService).register(eq("request-key"), any());
        verify(notifyBusinessEventApi).publish(any());
    }

    @Test
    void requestRejectsLegacyRoundBeforeCallingBpm() {
        round.setSupervisorConfirmationEnabled(false);

        ServiceException error = assertThrows(ServiceException.class,
                () -> service.request(ORDER_ID, REQUESTER_ID, request()));

        assertEquals(SALES_ORDER_SUPERVISOR_LEGACY_ROUND.getCode(), error.getCode());
        verifyNoInteractions(processTaskApi);
    }

    @Test
    void requestRejectsMissingDisabledAndSelfSupervisor() {
        mockOrdinaryTask();
        AdminUserRespDTO formalSales = user(FORMAL_SALES_ID, 10L, CommonStatusEnum.ENABLE.getStatus());
        when(adminUserApi.getUser(FORMAL_SALES_ID)).thenReturn(formalSales);
        DeptRespDTO dept = new DeptRespDTO(); dept.setId(10L);
        when(deptApi.getDept(10L)).thenReturn(dept);

        assertError(SALES_ORDER_SUPERVISOR_NOT_CONFIGURED.getCode());

        dept.setLeaderUserId(FORMAL_SALES_ID);
        assertError(SALES_ORDER_SUPERVISOR_SELF.getCode());

        dept.setLeaderUserId(SUPERVISOR_ID);
        when(adminUserApi.getUser(SUPERVISOR_ID)).thenReturn(user(SUPERVISOR_ID, 11L, CommonStatusEnum.DISABLE.getStatus()));
        assertError(SALES_ORDER_SUPERVISOR_DISABLED.getCode());
        verify(processTaskApi, never()).createBeforeSignTask(anyLong(), any());
    }

    @Test
    void requestRejectsSalesSupervisorWithoutConfirmationPermission() {
        mockOrdinaryTask();
        mockSupervisor(SUPERVISOR_ID, CommonStatusEnum.ENABLE.getStatus());
        when(permissionApi.hasAnyPermissions(SUPERVISOR_ID, "zsjos:sales-order:supervisor-confirm")).thenReturn(false);

        assertError(SALES_ORDER_SUPERVISOR_PERMISSION_NOT_GRANTED.getCode());

        verify(processTaskApi, never()).createBeforeSignTask(anyLong(), any());
    }

    @Test
    void requestRejectsSecondRequestForTheSameRoundAndCenter() {
        mockOrdinaryTask();
        when(confirmationMapper.selectByRoundAndTaskKey(ROUND_ID, TASK_REGISTRATION))
                .thenReturn(new SalesOrderSupervisorConfirmationDO());

        ServiceException error = assertThrows(ServiceException.class,
                () -> service.request(ORDER_ID, REQUESTER_ID, request()));

        assertEquals(SALES_ORDER_SUPERVISOR_ALREADY_REQUESTED.getCode(), error.getCode());
        verify(processTaskApi, never()).createBeforeSignTask(anyLong(), any());
    }

    @Test
    void confirmRequiresDesignatedSupervisorAndCompletesSignTask() {
        SalesOrderSupervisorConfirmationDO confirmation = pendingConfirmation();
        when(confirmationMapper.selectByIdForUpdate(300L, 1L)).thenReturn(confirmation);
        when(processTaskApi.getTodoTask(SUPERVISOR_ID, "supervisor-task-1")).thenReturn(signTask());

        service.decide(ORDER_ID, SUPERVISOR_ID, decision(), true);

        assertEquals(SUPERVISOR_CONFIRMED, confirmation.getStatus());
        assertEquals("同意主管确认", confirmation.getDecisionReason());
        assertEquals(1, confirmation.getVersion());
        ArgumentCaptor<BpmTaskDecisionReqDTO> captor = ArgumentCaptor.forClass(BpmTaskDecisionReqDTO.class);
        verify(processTaskApi).approveTask(eq(SUPERVISOR_ID), captor.capture());
        assertEquals("supervisor-task-1", captor.getValue().getTaskId());
        verify(processTaskApi, never()).rejectTask(anyLong(), any());
    }

    @Test
    void rejectEndsThroughBpmAndNonSupervisorCannotDecide() {
        SalesOrderSupervisorConfirmationDO confirmation = pendingConfirmation();
        when(confirmationMapper.selectByIdForUpdate(300L, 1L)).thenReturn(confirmation);

        ServiceException denied = assertThrows(ServiceException.class,
                () -> service.decide(ORDER_ID, 31L, decision(), false));
        assertEquals(SALES_ORDER_SUPERVISOR_PERMISSION_DENIED.getCode(), denied.getCode());

        when(processTaskApi.getTodoTask(SUPERVISOR_ID, "supervisor-task-1")).thenReturn(signTask());
        service.decide(ORDER_ID, SUPERVISOR_ID, decision(), false);

        assertEquals(SUPERVISOR_REJECTED, confirmation.getStatus());
        verify(processTaskApi).rejectTask(eq(SUPERVISOR_ID), any());
    }

    private void assertError(int code) {
        ServiceException error = assertThrows(ServiceException.class,
                () -> service.request(ORDER_ID, REQUESTER_ID, request()));
        assertEquals(code, error.getCode());
    }

    private void mockOrdinaryTask() {
        when(processTaskApi.getTodoTask(REQUESTER_ID, "task-1")).thenReturn(ordinaryTask());
    }

    private void mockSupervisor(Long leaderId, Integer status) {
        when(adminUserApi.getUser(FORMAL_SALES_ID)).thenReturn(user(FORMAL_SALES_ID, 10L, CommonStatusEnum.ENABLE.getStatus()));
        DeptRespDTO dept = new DeptRespDTO(); dept.setId(10L); dept.setLeaderUserId(leaderId);
        when(deptApi.getDept(10L)).thenReturn(dept);
        when(adminUserApi.getUser(leaderId)).thenReturn(user(leaderId, 11L, status));
        lenient().when(permissionApi.hasAnyPermissions(leaderId, "zsjos:sales-order:supervisor-confirm")).thenReturn(true);
    }

    private BpmTaskRespDTO ordinaryTask() {
        return new BpmTaskRespDTO().setId("task-1").setProcessInstanceId("process-1")
                .setBusinessKey(BUSINESS_KEY_PREFIX + ORDER_ID).setTaskDefinitionKey(TASK_REGISTRATION)
                .setSignTask(false);
    }

    private BpmTaskRespDTO signTask() {
        return new BpmTaskRespDTO().setId("supervisor-task-1").setProcessInstanceId("process-1")
                .setBusinessKey(BUSINESS_KEY_PREFIX + ORDER_ID).setTaskDefinitionKey(TASK_REGISTRATION)
                .setParentTaskId("task-1").setSignTask(true);
    }

    private SalesOrderSupervisorConfirmationDO pendingConfirmation() {
        SalesOrderSupervisorConfirmationDO row = new SalesOrderSupervisorConfirmationDO();
        row.setId(300L); row.setOrderId(ORDER_ID); row.setApprovalRoundId(ROUND_ID);
        row.setTaskDefinitionKey(TASK_REGISTRATION); row.setRequesterUserId(REQUESTER_ID);
        row.setSupervisorUserId(SUPERVISOR_ID); row.setParentTaskId("task-1");
        row.setSupervisorTaskId("supervisor-task-1"); row.setStatus(SUPERVISOR_PENDING); row.setVersion(0);
        return row;
    }

    private SalesOrderSupervisorRequestReqVO request() {
        SalesOrderSupervisorRequestReqVO req = new SalesOrderSupervisorRequestReqVO();
        req.setTaskId("task-1"); req.setApprovalRoundId(ROUND_ID); req.setOrderVersion(3); req.setRoundVersion(4);
        req.setReason("需要主管把关"); req.setIdempotencyKey("request-key");
        return req;
    }

    private SalesOrderSupervisorDecisionReqVO decision() {
        SalesOrderSupervisorDecisionReqVO req = new SalesOrderSupervisorDecisionReqVO();
        req.setConfirmationId(300L); req.setTaskId("supervisor-task-1"); req.setApprovalRoundId(ROUND_ID);
        req.setOrderVersion(3); req.setRoundVersion(4); req.setConfirmationVersion(0);
        req.setReason("同意主管确认"); req.setIdempotencyKey("decision-key");
        return req;
    }

    private AdminUserRespDTO user(Long id, Long deptId, Integer status) {
        AdminUserRespDTO user = new AdminUserRespDTO();
        user.setId(id); user.setDeptId(deptId); user.setStatus(status);
        return user;
    }
}
