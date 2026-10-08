package cn.iocoder.yudao.module.bpm.api.task;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.bpm.framework.flowable.core.enums.BpmnVariableConstants;
import cn.iocoder.yudao.module.bpm.service.task.BpmProcessInstanceService;
import cn.iocoder.yudao.module.bpm.service.task.BpmTaskService;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import cn.iocoder.yudao.module.system.api.user.dto.AdminUserRespDTO;
import org.flowable.engine.TaskService;
import org.flowable.task.api.Task;
import org.flowable.task.api.TaskQuery;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BpmProcessProgressApiImplTest {
    @InjectMocks private BpmProcessProgressApiImpl api;
    @Mock private TaskService flowableTaskService;
    @Mock private BpmTaskService taskService;
    @Mock private BpmProcessInstanceService instanceService;
    @Mock private AdminUserApi userApi;
    @BeforeEach void setup() { TenantContextHolder.setTenantId(1L); }
    @AfterEach void cleanup() { TenantContextHolder.clear(); }
    @Test void batchesTenantScopedLiveAssignmentsExcludingWaitingSigningAndSuspendedTasks() {
        var query = mock(TaskQuery.class, RETURNS_SELF);
        when(flowableTaskService.createTaskQuery()).thenReturn(query);
        Task assigned = task("assigned",1,"1",false,"22");
        Task signedChild = task("signed-child",1,"1",false,"23");
        Task waiting = task("waiting-parent",0,"1",false,"24");
        Task approvedWaiting = task("approved-parent",7,"1",false,"25");
        Task suspended = task("suspended",1,"1",true,"26");
        Task otherTenant = task("other-tenant",1,"2",false,"27");
        when(query.list()).thenReturn(List.of(assigned,signedChild,waiting,approvedWaiting,suspended,otherTenant));
        var user = new AdminUserRespDTO();user.setNickname("当前受托审批人");
        when(userApi.getUserMap(Set.of(22L,23L))).thenReturn(Map.of(22L,user,23L,user));
        var result=api.getCurrentTasks(Set.of("process"));
        assertEquals(List.of("assigned","signed-child"),result.get("process").stream().map(t->t.id()).toList());
        verify(query).taskTenantId("1");verify(query).includeTaskLocalVariables();verify(query).processInstanceIdIn(List.of("process"));
    }
    @Test void emptyInputDoesNotQueryEngine() { assertTrue(api.getCurrentTasks(Set.of()).isEmpty());verifyNoInteractions(flowableTaskService); }
    @Test void unassignedTaskIsVisibleWithoutInventingRecipient() {
        var query=mock(TaskQuery.class,RETURNS_SELF);when(flowableTaskService.createTaskQuery()).thenReturn(query);
        var unassigned = task("unassigned",1,"1",false,null);
        when(query.list()).thenReturn(List.of(unassigned));when(userApi.getUserMap(Set.of())).thenReturn(Map.of());
        assertNull(api.getCurrentTasks(Set.of("process")).get("process").getFirst().assigneeUserId());
    }
    @Test void crossTenantHistoryIsRejectedBeforeReadingVariablesOrTasks() {
        var instance=mock(org.flowable.engine.history.HistoricProcessInstance.class);
        when(instance.getTenantId()).thenReturn("2");when(instanceService.getHistoricProcessInstance("foreign")).thenReturn(instance);
        assertThrows(cn.iocoder.yudao.framework.common.exception.ServiceException.class,()->api.getProgress("foreign",11L));
        verify(instanceService,never()).getApprovalDetail(any(),any());verifyNoInteractions(taskService);
    }
    private Task task(String id,int status,String tenant,boolean suspended,String assignee) {
        Task task=mock(Task.class,withSettings().lenient());
        when(task.getId()).thenReturn(id);when(task.getTenantId()).thenReturn(tenant);when(task.isSuspended()).thenReturn(suspended);
        when(task.getTaskLocalVariables()).thenReturn(Map.of(BpmnVariableConstants.TASK_VARIABLE_STATUS,status));
        when(task.getAssignee()).thenReturn(assignee);when(task.getProcessInstanceId()).thenReturn("process");when(task.getName()).thenReturn("审核");
        return task;
    }
}
