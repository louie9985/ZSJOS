package cn.iocoder.yudao.module.zsjos.service.lead;

import cn.iocoder.yudao.module.bpm.enums.task.BpmProcessInstanceStatusEnum;
import cn.iocoder.yudao.module.bpm.api.task.BpmProcessInstanceApi;
import cn.iocoder.yudao.module.bpm.api.task.dto.BpmProcessInstanceCreateReqDTO;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.system.api.dept.DeptApi;
import cn.iocoder.yudao.module.system.api.dept.dto.DeptRespDTO;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import cn.iocoder.yudao.module.system.api.user.dto.AdminUserRespDTO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.LeadTransferRequestDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.LeadAgingPoolCycleDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.LeadDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.lead.LeadAgingPoolCycleMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.lead.LeadMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.lead.LeadTransferRequestMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.order.SalesOrderMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants.LEAD_AGING_POOL_STATE_INVALID;
import static cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants.LEAD_PERMISSION_DENIED;
import static cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants.LEAD_AGING_POOL_IDEMPOTENCY_CONFLICT;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LeadTransferRequestServiceImplTest {
    @InjectMocks private LeadTransferRequestServiceImpl service;
    @Mock private LeadTransferRequestMapper requestMapper;
    @Mock private LeadAgingPoolCycleMapper cycleMapper;
    @Mock private LeadMapper leadMapper;
    @Mock private LeadAgingPoolService agingPoolService;
    @Mock private LeadDispatchService dispatchService;
    @Mock private SalesOrderMapper orderMapper;
    @Mock private LeadNotifyEventPublisher notifyEventPublisher;
    @Mock private AdminUserApi adminUserApi;
    @Mock private DeptApi deptApi;
    @Mock private BpmProcessInstanceApi processInstanceApi;

    @BeforeEach void setUp() { TenantContextHolder.setTenantId(1L); }
    @AfterEach void tearDown() { TenantContextHolder.clear(); }

    @Test
    void approvedBpmResultExecutesFormalTransfer() {
        LeadTransferRequestDO request = new LeadTransferRequestDO();
        request.setId(1L); request.setLeadId(2L); request.setFromOwnerUserId(10L);
        request.setRequestedOwnerUserId(20L);
        request.setReason("本人持续跟进"); request.setStatus("pending");
        when(requestMapper.selectByProcessInstanceIdForUpdate("p1", 1L)).thenReturn(request);
        when(dispatchService.tryAdminTransfer(2L, 10L, 20L, 20L,
                "同团队销售转派申请审批通过：本人持续跟进"))
                .thenReturn(LeadDispatchService.TransferAttemptResult.success());

        service.handleProcessResult("p1", BpmProcessInstanceStatusEnum.APPROVE.getStatus(), "通过");

        verify(dispatchService).tryAdminTransfer(2L, 10L, 20L, 20L,
                "同团队销售转派申请审批通过：本人持续跟进");
        assertEquals("approved", request.getStatus());
        verify(requestMapper).updateById(request);
    }

    @Test
    void createRejectsEndedCycleAfterLockAndAuthorizationWhenNoReplayExists() {
        LeadAgingPoolCycleDO cycle = new LeadAgingPoolCycleDO();
        cycle.setId(3L); cycle.setLeadId(2L); cycle.setStatus("exited"); cycle.setCollaboratorUserId(20L);
        LeadDO lead = new LeadDO(); lead.setId(2L); lead.setOwnerUserId(10L);
        when(cycleMapper.selectById(3L)).thenReturn(cycle);
        when(cycleMapper.selectByIdForUpdate(3L, 1L)).thenReturn(cycle);
        when(leadMapper.selectByIdForUpdate(2L, 1L)).thenReturn(lead);
        when(agingPoolService.canRead(cycle, 20L)).thenReturn(true);

        var error = assertThrows(cn.iocoder.yudao.framework.common.exception.ServiceException.class,
                () -> service.create(3L, 20L,
                        new cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.agingpool.LeadTransferRequestCreateReqVO()
                                .setIdempotencyKey("transfer-1").setReason("持续跟进")));

        assertEquals(LEAD_AGING_POOL_STATE_INVALID.getCode(), error.getCode());
        verify(agingPoolService).canRead(cycle, 20L);
    }

    @Test
    void createReplaysCompletedRequestBeforeCheckingMutableCycleState() {
        LeadAgingPoolCycleDO cycle = new LeadAgingPoolCycleDO();
        cycle.setId(3L); cycle.setLeadId(2L); cycle.setStatus("exited"); cycle.setCollaboratorUserId(20L);
        LeadDO lead = new LeadDO(); lead.setId(2L); lead.setOwnerUserId(10L);
        LeadTransferRequestDO replay = new LeadTransferRequestDO();
        replay.setId(9L); replay.setLeadId(2L); replay.setRequestedOwnerUserId(20L);
        when(cycleMapper.selectById(3L)).thenReturn(cycle);
        when(cycleMapper.selectByIdForUpdate(3L, 1L)).thenReturn(cycle);
        when(leadMapper.selectByIdForUpdate(2L, 1L)).thenReturn(lead);
        when(agingPoolService.canRead(cycle, 20L)).thenReturn(true);
        when(requestMapper.selectByIdempotencyKey("transfer-completed")).thenReturn(replay);

        Long result = service.create(3L, 20L,
                new cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.agingpool.LeadTransferRequestCreateReqVO()
                        .setIdempotencyKey("transfer-completed").setReason("持续跟进"));

        assertEquals(9L, result);
        verify(agingPoolService).canRead(cycle, 20L);
        verifyNoInteractions(adminUserApi, deptApi, processInstanceApi, notifyEventPublisher);
    }

    @Test
    void createRejectsRequesterWithoutCycleVisibilityAfterLock() {
        LeadAgingPoolCycleDO snapshot = new LeadAgingPoolCycleDO();
        snapshot.setId(3L); snapshot.setLeadId(2L); snapshot.setStatus("assigned");
        LeadDO lead = new LeadDO(); lead.setId(2L); lead.setOwnerUserId(10L);
        when(cycleMapper.selectById(3L)).thenReturn(snapshot);
        when(leadMapper.selectByIdForUpdate(2L, 1L)).thenReturn(lead);
        when(cycleMapper.selectByIdForUpdate(3L, 1L)).thenReturn(snapshot);
        when(agingPoolService.canRead(snapshot, 20L)).thenReturn(false);

        var error = assertThrows(cn.iocoder.yudao.framework.common.exception.ServiceException.class,
                () -> service.create(3L, 20L,
                        new cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.agingpool.LeadTransferRequestCreateReqVO()
                                .setIdempotencyKey("transfer-2").setReason("持续跟进")));

        assertEquals(LEAD_PERMISSION_DENIED.getCode(), error.getCode());
        var locks = inOrder(cycleMapper, leadMapper);
        locks.verify(cycleMapper).selectByIdForUpdate(3L, 1L);
        locks.verify(leadMapper).selectByIdForUpdate(2L, 1L);
    }

    @Test
    void createRejectsVisibleSalesWhoAreNotCurrentCollaborator() {
        LeadAgingPoolCycleDO cycle = new LeadAgingPoolCycleDO();
        cycle.setId(3L); cycle.setLeadId(2L); cycle.setStatus("assigned"); cycle.setCollaboratorUserId(20L);
        LeadDO lead = new LeadDO(); lead.setId(2L); lead.setOwnerUserId(10L);
        when(cycleMapper.selectById(3L)).thenReturn(cycle);
        when(cycleMapper.selectByIdForUpdate(3L, 1L)).thenReturn(cycle);
        when(leadMapper.selectByIdForUpdate(2L, 1L)).thenReturn(lead);
        when(agingPoolService.canRead(cycle, 30L)).thenReturn(true);

        var error = assertThrows(cn.iocoder.yudao.framework.common.exception.ServiceException.class,
                () -> service.create(3L, 30L,
                        new cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.agingpool.LeadTransferRequestCreateReqVO()
                                .setIdempotencyKey("transfer-not-b").setReason("持续跟进")));

        assertEquals(LEAD_PERMISSION_DENIED.getCode(), error.getCode());
        verifyNoInteractions(adminUserApi, deptApi, processInstanceApi, notifyEventPublisher);
    }

    @Test
    void createRejectsIdempotencyReplayFromAnotherCycleAfterAuthorization() {
        LeadAgingPoolCycleDO cycle = new LeadAgingPoolCycleDO();
        cycle.setId(3L); cycle.setLeadId(2L); cycle.setStatus("assigned"); cycle.setCollaboratorUserId(20L);
        LeadDO lead = new LeadDO(); lead.setId(2L); lead.setOwnerUserId(10L);
        LeadTransferRequestDO replay = new LeadTransferRequestDO();
        replay.setId(9L); replay.setLeadId(99L); replay.setRequestedOwnerUserId(20L);
        when(cycleMapper.selectById(3L)).thenReturn(cycle);
        when(cycleMapper.selectByIdForUpdate(3L, 1L)).thenReturn(cycle);
        when(leadMapper.selectByIdForUpdate(2L, 1L)).thenReturn(lead);
        when(agingPoolService.canRead(cycle, 20L)).thenReturn(true);
        when(requestMapper.selectByIdempotencyKey("transfer-replay")).thenReturn(replay);

        var error = assertThrows(cn.iocoder.yudao.framework.common.exception.ServiceException.class,
                () -> service.create(3L, 20L,
                        new cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.agingpool.LeadTransferRequestCreateReqVO()
                                .setIdempotencyKey("transfer-replay").setReason("持续跟进")));

        assertEquals(LEAD_AGING_POOL_IDEMPOTENCY_CONFLICT.getCode(), error.getCode());
        verify(agingPoolService).canRead(cycle, 20L);
    }

    @Test
    void createReturnsConcurrentReplayAfterUniqueKeyCollision() {
        prepareCreateThroughInsert("transfer-race");
        LeadTransferRequestDO replay = new LeadTransferRequestDO();
        replay.setId(9L); replay.setLeadId(2L); replay.setRequestedOwnerUserId(20L);
        doThrow(new DuplicateKeyException("duplicate idempotency key"))
                .when(requestMapper).insert(any(LeadTransferRequestDO.class));
        when(requestMapper.selectByIdempotencyKeyForUpdate("transfer-race", 1L)).thenReturn(replay);

        Long result = service.create(3L, 20L,
                new cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.agingpool.LeadTransferRequestCreateReqVO()
                        .setIdempotencyKey("transfer-race").setReason("持续跟进"));

        assertEquals(9L, result);
        verify(requestMapper).selectByIdempotencyKeyForUpdate("transfer-race", 1L);
        verifyNoInteractions(processInstanceApi, notifyEventPublisher);
    }

    @Test
    void createRejectsConcurrentIdempotencyCollisionFromAnotherLead() {
        prepareCreateThroughInsert("transfer-collision");
        LeadTransferRequestDO replay = new LeadTransferRequestDO();
        replay.setId(9L); replay.setLeadId(99L); replay.setRequestedOwnerUserId(20L);
        doThrow(new DuplicateKeyException("duplicate idempotency key"))
                .when(requestMapper).insert(any(LeadTransferRequestDO.class));
        when(requestMapper.selectByIdempotencyKeyForUpdate("transfer-collision", 1L)).thenReturn(replay);

        var error = assertThrows(cn.iocoder.yudao.framework.common.exception.ServiceException.class,
                () -> service.create(3L, 20L,
                        new cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.agingpool.LeadTransferRequestCreateReqVO()
                                .setIdempotencyKey("transfer-collision").setReason("持续跟进")));

        assertEquals(LEAD_AGING_POOL_IDEMPOTENCY_CONFLICT.getCode(), error.getCode());
        verifyNoInteractions(processInstanceApi, notifyEventPublisher);
    }

    @Test
    void createPublishesLeadNumberToBpm() {
        prepareCreateThroughInsert("transfer-number");
        doAnswer(invocation -> { ((LeadTransferRequestDO) invocation.getArgument(0)).setId(9L); return 1; })
                .when(requestMapper).insert(any(LeadTransferRequestDO.class));
        when(processInstanceApi.createProcessInstance(eq(20L), any())).thenReturn("process-1");

        Long result = service.create(3L, 20L,
                new cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.agingpool.LeadTransferRequestCreateReqVO()
                        .setIdempotencyKey("transfer-number").setReason("持续跟进"));

        assertEquals(9L, result);
        ArgumentCaptor<BpmProcessInstanceCreateReqDTO> captor =
                ArgumentCaptor.forClass(BpmProcessInstanceCreateReqDTO.class);
        verify(processInstanceApi).createProcessInstance(eq(20L), captor.capture());
        assertEquals("KZ202608160000000002", captor.getValue().getVariables().get("leadNo"));
        assertEquals(2L, captor.getValue().getVariables().get("leadId"));
    }

    private void prepareCreateThroughInsert(String idempotencyKey) {
        LeadAgingPoolCycleDO cycle = new LeadAgingPoolCycleDO();
        cycle.setId(3L); cycle.setLeadId(2L); cycle.setStatus("assigned"); cycle.setCollaboratorUserId(20L);
        LeadDO lead = new LeadDO(); lead.setId(2L); lead.setLeadNo("KZ202608160000000002"); lead.setOwnerUserId(10L);
        when(cycleMapper.selectById(3L)).thenReturn(cycle);
        when(cycleMapper.selectByIdForUpdate(3L, 1L)).thenReturn(cycle);
        when(leadMapper.selectByIdForUpdate(2L, 1L)).thenReturn(lead);
        when(agingPoolService.canRead(cycle, 20L)).thenReturn(true);
        when(requestMapper.selectByIdempotencyKey(idempotencyKey)).thenReturn(null);
        AdminUserRespDTO owner = new AdminUserRespDTO(); owner.setId(10L); owner.setDeptId(100L);
        AdminUserRespDTO requester = new AdminUserRespDTO(); requester.setId(20L); requester.setDeptId(100L);
        when(adminUserApi.getUser(10L)).thenReturn(owner);
        when(adminUserApi.getUser(20L)).thenReturn(requester);
        DeptRespDTO dept = new DeptRespDTO(); dept.setId(100L); dept.setLeaderUserId(30L);
        when(deptApi.getDept(100L)).thenReturn(dept);
    }
}
