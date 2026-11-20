package cn.iocoder.yudao.module.zsjos.service.order;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.module.bpm.api.task.BpmProcessTaskApi;
import cn.iocoder.yudao.module.bpm.api.task.BpmProcessTaskApi.BpmLiveTaskState;
import cn.iocoder.yudao.module.bpm.api.task.dto.BpmTaskDecisionReqDTO;
import cn.iocoder.yudao.module.bpm.api.task.dto.BpmTaskRespDTO;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import cn.iocoder.yudao.module.zsjos.controller.admin.order.vo.SalesOrderNativeActionReqVO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.order.SalesOrderApprovalRoundDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.order.SalesOrderDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.order.SalesOrderApprovalRoundMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.order.SalesOrderMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static cn.iocoder.yudao.module.zsjos.enums.SalesOrderConstants.*;
import static cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants.SALES_ORDER_PERMISSION_DENIED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SalesOrderNativeApprovalAccessTest {

    @Mock private BpmProcessTaskApi processTaskApi;
    @Mock private PermissionApi nativeApprovalPermissionApi;
    @Mock private SalesOrderMapper orderMapper;
    @Mock private SalesOrderApprovalRoundMapper roundMapper;
    @Mock private SalesOrderObjectPermissionService permissionService;
    @InjectMocks private SalesOrderServiceImpl service;

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {FINANCE_PROCESS_DEFINITION_KEY, PROCESS_DEFINITION_KEY})
    void businessReviewerCanApproveThroughBpmEngineWithoutBpmMenuPermission(String processKey) {
        grantBusinessAccess();
        bindTask(43L, false, 42L, processKey);
        when(processTaskApi.getCommittedNodeTasks("finance-process", TASK_FINANCE))
                .thenReturn(List.of(new BpmLiveTaskState("finance-task", null, "20", null, 1)));

        service.actOnNativeFinanceApproval(20L, decision());

        var expected = new BpmTaskDecisionReqDTO();
        expected.setTaskId("finance-task");
        expected.setReason("reviewed");
        verify(processTaskApi).approveTask(20L, expected);
        verify(nativeApprovalPermissionApi, never()).hasAnyPermissions(20L, "bpm:task:query");
        verify(nativeApprovalPermissionApi, never()).hasAnyPermissions(20L, "bpm:task:update");
    }

    @Test
    void businessReviewerCannotActOnAnOrderWhoseLatestRoundHasChanged() {
        grantBusinessAccess();
        bindCurrentTask(99L);

        assertDenied();
        verify(processTaskApi, never()).approveTask(eq(20L), any());
    }

    @Test
    void businessReviewerCannotSubmitDecisionForSomeoneElsesTask() {
        when(nativeApprovalPermissionApi.hasAnyPermissions(20L, PERMISSION_REVIEW)).thenReturn(true);

        assertDenied();
        verifyNoInteractions(orderMapper, roundMapper);
        verify(processTaskApi, never()).approveTask(eq(20L), any());
    }

    @Test
    void waitingOwnerCannotApproveEvenIfSnapshotSaysRunning() {
        grantBusinessAccess();
        bindCurrentTask(43L);
        when(processTaskApi.getCommittedNodeTasks("finance-process", TASK_FINANCE))
                .thenReturn(List.of(new BpmLiveTaskState("finance-task", null, null, "before", 0)));

        assertDenied();
        verify(processTaskApi, never()).approveTask(eq(20L), any());
    }

    @Test
    void nonFinanceSignChildCanApproveWithOnlyBusinessMenuPermission() {
        when(nativeApprovalPermissionApi.hasAnyPermissions(20L, PERMISSION_REVIEW)).thenReturn(true);
        bindTask(43L, true, 42L);
        when(processTaskApi.getCommittedNodeTasks("finance-process", TASK_FINANCE))
                .thenReturn(List.of(new BpmLiveTaskState("finance-task", "parent", "20", null, 1)));

        service.actOnNativeFinanceApproval(20L, decision());

        verify(processTaskApi).approveTask(eq(20L), any());
        verifyNoInteractions(permissionService);
        verify(nativeApprovalPermissionApi, never()).hasAnyPermissions(20L, "bpm:task:update");
    }

    @Test
    void ownSignTaskCannotReadOrActOnAnUnrelatedOrderBinding() {
        when(nativeApprovalPermissionApi.hasAnyPermissions(20L, PERMISSION_REVIEW)).thenReturn(true);
        bindTask(43L, true, 99L);

        assertDenied();
        assertThrows(ServiceException.class, () -> service.getNativeFinanceApproval("finance-task", false, 20L, true));
        verifyNoInteractions(orderMapper, permissionService);
        verify(processTaskApi, never()).approveTask(eq(20L), any());
    }

    private void grantBusinessAccess() {
        when(nativeApprovalPermissionApi.hasAnyPermissions(20L, PERMISSION_REVIEW)).thenReturn(true);
        when(permissionService.isFinanceCenterMember(20L)).thenReturn(true);
    }

    private void bindCurrentTask(Long currentRoundId) {
        bindTask(currentRoundId, false, 42L);
    }

    private void bindTask(Long currentRoundId, boolean signChild, Long businessOrderId) {
        bindTask(currentRoundId, signChild, businessOrderId, FINANCE_PROCESS_DEFINITION_KEY);
    }

    private void bindTask(Long currentRoundId, boolean signChild, Long businessOrderId, String processKey) {
        var task = new BpmTaskRespDTO();
        task.setId("finance-task");
        task.setProcessDefinitionKey(processKey);
        task.setProcessInstanceId("finance-process");
        task.setBusinessKey(BUSINESS_KEY_PREFIX + businessOrderId);
        task.setSignTask(signChild);
        if (signChild) task.setParentTaskId("parent");
        task.setTaskDefinitionKey(TASK_FINANCE);
        task.setStatus(1);
        when(processTaskApi.getParticipantTodoTask(20L, "finance-task")).thenReturn(task);
        var round = new SalesOrderApprovalRoundDO();
        round.setId(43L);
        round.setOrderId(42L);
        round.setProcessDefinitionKey(processKey);
        round.setStatus(ROUND_PENDING);
        when(roundMapper.selectByProcessInstanceId("finance-process")).thenReturn(round);
        var order = new SalesOrderDO();
        order.setId(42L);
        order.setStatus(STATUS_PENDING_APPROVAL);
        order.setCurrentApprovalRoundId(currentRoundId);
        if (businessOrderId.equals(42L)) when(orderMapper.selectById(42L)).thenReturn(order);
        if (!signChild) when(permissionService.canRead(order, 20L)).thenReturn(true);
    }

    private SalesOrderNativeActionReqVO decision() {
        var request = new SalesOrderNativeActionReqVO();
        request.setTaskId("finance-task");
        request.setAction("approve");
        request.setReason("  reviewed  ");
        return request;
    }

    private void assertDenied() {
        assertEquals(SALES_ORDER_PERMISSION_DENIED.getCode(), assertThrows(ServiceException.class,
                () -> service.actOnNativeFinanceApproval(20L, decision())).getCode());
    }
}
