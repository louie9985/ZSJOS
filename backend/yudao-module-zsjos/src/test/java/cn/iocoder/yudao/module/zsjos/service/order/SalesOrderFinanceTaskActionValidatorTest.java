package cn.iocoder.yudao.module.zsjos.service.order;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.bpm.api.task.BpmProcessTaskApi;
import cn.iocoder.yudao.module.bpm.api.task.BpmProcessTaskApi.BpmLiveTaskState;
import cn.iocoder.yudao.module.bpm.api.task.dto.BpmTaskActionContext;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import cn.iocoder.yudao.module.system.api.user.dto.AdminUserRespDTO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.order.SalesOrderApprovalRoundDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.order.SalesOrderDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.order.SalesOrderApprovalRoundMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.order.SalesOrderMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.List;
import java.util.Set;

import static cn.iocoder.yudao.module.bpm.api.task.BpmTaskActionValidator.*;
import static cn.iocoder.yudao.module.zsjos.enums.SalesOrderConstants.*;
import static cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SalesOrderFinanceTaskActionValidatorTest {
    @Mock private SalesOrderMapper orderMapper;
    @Mock private SalesOrderApprovalRoundMapper roundMapper;
    @Mock private BpmProcessTaskApi processTaskApi;
    @Mock private SalesOrderObjectPermissionService orderPermissionService;
    @Mock private PermissionApi permissionApi;
    @Mock private AdminUserApi adminUserApi;
    @InjectMocks private SalesOrderTaskActionValidator validator;

    @BeforeEach
    void currentOrder() {
        TenantContextHolder.setTenantId(1L);
        var order = new SalesOrderDO();
        order.setId(42L);
        order.setStatus(STATUS_PENDING_APPROVAL);
        order.setCurrentApprovalRoundId(43L);
        var round = new SalesOrderApprovalRoundDO();
        round.setId(43L);
        round.setOrderId(42L);
        round.setProcessInstanceId("finance-order");
        round.setStatus(ROUND_PENDING);
        when(roundMapper.selectByProcessInstanceId("finance-order")).thenReturn(round);
        when(roundMapper.selectByIdForUpdate(43L, 1L)).thenReturn(round);
        when(orderMapper.selectByIdForUpdate(42L, 1L)).thenReturn(order);
    }

    @AfterEach
    void clearTenant() {
        TenantContextHolder.clear();
    }

    @Test
    void anotherFinanceReviewerCannotFinishOrSignWhileRequiredSiblingSignIsOpen() {
        grantFinanceReview();
        when(processTaskApi.getCommittedNodeTasks("finance-order", TASK_FINANCE)).thenReturn(List.of(
                new BpmLiveTaskState("other-finance", null, "20", null, 1),
                new BpmLiveTaskState("first-finance", null, "21", "after", 7),
                new BpmLiveTaskState("sign-child", "first-finance", "30", null, 1)));
        var task = context(20L, "other-finance");
        assertEquals(SALES_ORDER_FINANCE_SIGN_PENDING.getCode(),
                assertThrows(ServiceException.class, () -> validator.validate(task)).getCode());
        assertEquals(SALES_ORDER_FINANCE_SIGN_PENDING.getCode(),
                assertThrows(ServiceException.class, () -> validator.validate(task.setAction(ACTION_ADD_SIGN))).getCode());
    }

    @Test
    void ordinaryEmployeeWithBpmPermissionsIsEligibleForEitherSignDirection() {
        grantFinanceReview();
        when(processTaskApi.getCommittedNodeTasks("finance-order", TASK_FINANCE)).thenReturn(List.of(
                new BpmLiveTaskState("finance", null, "20", null, 1)));
        var employee = new AdminUserRespDTO();
        employee.setId(30L);
        employee.setStatus(0);
        when(adminUserApi.getUserList(Set.of(30L))).thenReturn(List.of(employee));
        when(permissionApi.hasAnyPermissions(30L, PERMISSION_REVIEW)).thenReturn(false);
        when(permissionApi.hasAnyPermissions(30L, "bpm:task:query")).thenReturn(true);
        when(permissionApi.hasAnyPermissions(30L, "bpm:task:update")).thenReturn(true);

        var sign = context(20L, "finance").setAction(ACTION_ADD_SIGN).setReason("Support the finance review")
                .setSignUserIds(Set.of(30L));
        assertDoesNotThrow(() -> validator.validate(sign.setSignType("before")));
        assertDoesNotThrow(() -> validator.validate(sign.setSignType("after")));
        assertEquals(SALES_ORDER_PERMISSION_DENIED.getCode(),
                assertThrows(ServiceException.class, () -> validator.validate(sign.setSignType("parallel"))).getCode());
        verify(orderPermissionService, never()).isFinanceCenterMember(30L);
    }

    @Test
    void matchingChildCanApproveWithoutFinanceMembership() {
        when(permissionApi.hasAnyPermissions(30L, "bpm:task:update", PERMISSION_REVIEW)).thenReturn(true);
        when(processTaskApi.getCommittedNodeTasks("finance-order", TASK_FINANCE)).thenReturn(List.of(
                new BpmLiveTaskState("finance", null, "20", "after", 7),
                new BpmLiveTaskState("sign-child", "finance", "30", null, 1)));
        var child = context(30L, "sign-child").setParentTaskId("finance").setReason("Reviewed");
        assertDoesNotThrow(() -> validator.validate(child));
        verifyNoInteractions(orderPermissionService);
        verify(permissionApi, never()).hasAnyPermissions(30L, PERMISSION_REVIEW);
    }

    @Test
    void staleScopeIsRejectedUsingCommittedState() {
        grantFinanceReview();
        when(processTaskApi.getCommittedNodeTasks("finance-order", TASK_FINANCE)).thenReturn(List.of(
                new BpmLiveTaskState("finance", null, "20", "after", 1)));
        assertEquals(SALES_ORDER_ALREADY_HANDLED.getCode(), assertThrows(ServiceException.class,
                () -> validator.validate(context(20L, "finance"))).getCode());
    }

    private void grantFinanceReview() {
        when(permissionApi.hasAnyPermissions(20L, PERMISSION_REVIEW)).thenReturn(true);
        when(orderPermissionService.isFinanceCenterMember(20L)).thenReturn(true);
    }

    private BpmTaskActionContext context(Long assignee, String taskId) {
        return new BpmTaskActionContext().setProcessDefinitionKey(FINANCE_PROCESS_DEFINITION_KEY)
                .setUserId(assignee).setAssignee(String.valueOf(assignee)).setTaskId(taskId)
                .setTaskStatus(1).setAction(ACTION_APPROVE).setTenantId("1").setTaskDefinitionKey(TASK_FINANCE)
                .setProcessInstanceId("finance-order").setBusinessKey(BUSINESS_KEY_PREFIX + 42L);
    }
}
