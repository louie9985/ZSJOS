package cn.iocoder.yudao.module.bpm.service.task;

import cn.iocoder.yudao.framework.test.core.ut.BaseMockitoUnitTest;
import cn.iocoder.yudao.module.bpm.controller.admin.task.vo.task.BpmTaskSignCreateReqVO;
import cn.iocoder.yudao.module.bpm.controller.admin.task.vo.task.BpmTaskApproveReqVO;
import cn.iocoder.yudao.module.bpm.enums.task.BpmTaskStatusEnum;
import cn.iocoder.yudao.module.bpm.framework.flowable.core.enums.BpmnVariableConstants;
import cn.iocoder.yudao.module.bpm.service.comment.BpmCommentService;
import cn.iocoder.yudao.module.bpm.service.definition.BpmModelService;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import cn.iocoder.yudao.module.system.api.user.dto.AdminUserRespDTO;
import org.flowable.bpmn.model.BpmnModel;
import org.flowable.bpmn.model.UserTask;
import org.flowable.engine.ManagementService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.flowable.engine.runtime.ProcessInstance;
import org.flowable.task.api.NativeTaskQuery;
import org.flowable.task.api.TaskQuery;
import org.flowable.task.service.impl.persistence.entity.TaskEntity;
import org.flowable.task.service.impl.persistence.entity.TaskEntityImpl;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BpmTaskServiceImplParallelSignTest extends BaseMockitoUnitTest {

    @InjectMocks
    private BpmTaskServiceImpl service;
    @Mock
    private TaskService taskService;
    @Mock
    private AdminUserApi adminUserApi;
    @Mock
    private BpmCommentService commentService;
    @Mock
    private TaskQuery taskQuery;
    @Mock
    private BpmProcessInstanceService processInstanceService;
    @Mock
    private BpmModelService modelService;
    @Mock
    private RuntimeService runtimeService;
    @Mock
    private ManagementService managementService;
    @Mock
    private NativeTaskQuery nativeTaskQuery;

    @Test
    void createParallelSignKeepsParentAssignedAndCreatesActiveChild() {
        TaskEntityImpl parent = spy(new TaskEntityImpl());
        parent.setId("parent-task"); parent.setAssignee("233"); parent.setProcessInstanceId("process-1");
        parent.setTaskDefinitionKey("financeReview"); parent.setName("财务审批");
        TaskEntityImpl child = new TaskEntityImpl(); child.setId("child-task");
        AdminUserRespDTO supervisor = new AdminUserRespDTO(); supervisor.setId(300L); supervisor.setNickname("销售主管");
        AdminUserRespDTO reviewer = new AdminUserRespDTO(); reviewer.setId(233L); reviewer.setNickname("财务审批人");
        when(taskService.createTaskQuery()).thenReturn(taskQuery);
        when(taskQuery.taskId("parent-task")).thenReturn(taskQuery);
        when(taskQuery.includeTaskLocalVariables()).thenReturn(taskQuery);
        when(taskQuery.singleResult()).thenReturn(parent);
        when(taskQuery.processInstanceId("process-1")).thenReturn(taskQuery);
        when(taskQuery.taskDefinitionKey("financeReview")).thenReturn(taskQuery);
        when(taskQuery.list()).thenReturn(List.of(parent));
        when(taskService.newTask(anyString())).thenReturn(child);
        when(adminUserApi.getUserList(Set.of(300L))).thenReturn(List.of(supervisor));
        when(adminUserApi.getUser(233L)).thenReturn(reviewer);

        List<String> taskIds = service.createSignTask(233L, new BpmTaskSignCreateReqVO()
                .setId("parent-task").setUserIds(Set.of(300L)).setType("parallel").setReason("需要主管审批"));

        assertEquals(List.of(child.getId()), taskIds);
        assertEquals("233", parent.getAssignee());
        assertNull(parent.getOwner());
        assertEquals("parallel", parent.getScopeType());
        assertEquals("300", child.getAssignee());
        assertEquals("parent-task", child.getParentTaskId());
        ArgumentCaptor<TaskEntityImpl> saved = ArgumentCaptor.forClass(TaskEntityImpl.class);
        verify(taskService, org.mockito.Mockito.times(2)).saveTask(saved.capture());
        assertEquals(List.of(parent, child), saved.getAllValues());
    }

    @Test
    void approveParentFirstWaitsForParallelSignChild() {
        TaskEntityImpl parent = parentTask(null);
        ProcessInstance instance = org.mockito.Mockito.mock(ProcessInstance.class);
        when(taskService.createTaskQuery()).thenReturn(taskQuery);
        when(taskQuery.taskId("parent-task")).thenReturn(taskQuery);
        when(taskQuery.includeTaskLocalVariables()).thenReturn(taskQuery);
        when(taskQuery.singleResult()).thenReturn(parent);
        when(processInstanceService.getProcessInstance("process-1")).thenReturn(instance);
        when(instance.getProcessVariables()).thenReturn(Map.of());
        when(modelService.getBpmnModelByDefinitionId("definition-1")).thenReturn(model());

        service.approveTask(233L, new BpmTaskApproveReqVO().setId("parent-task").setReason("中心通过"));

        verify(taskService).setVariableLocal("parent-task", BpmnVariableConstants.TASK_VARIABLE_STATUS,
                BpmTaskStatusEnum.APPROVING.getStatus());
        verify(taskService).setOwner("parent-task", "233");
        verify(taskService).setAssignee("parent-task", null);
        verify(taskService, never()).saveTask(parent);
        verify(taskService, never()).complete(eq("parent-task"), org.mockito.ArgumentMatchers.<Map<String, Object>>any(), eq(true));
    }

    @Test
    void supervisorApprovalCompletesPreviouslyApprovedParent() {
        TaskEntityImpl parent = parentTask(BpmTaskStatusEnum.APPROVING.getStatus());
        parent.setAssignee(null); parent.setOwner("233");
        mockNoRemainingChildren(parent);

        ReflectionTestUtils.invokeMethod(service, "handleParentTaskIfSign", "parent-task");

        assertNull(parent.getScopeType());
        verify(taskService).resolveTask("parent-task");
        verify(taskService).setVariableLocal("parent-task", BpmnVariableConstants.TASK_VARIABLE_STATUS,
                BpmTaskStatusEnum.APPROVE.getStatus());
        verify(taskService).complete("parent-task");
    }

    @Test
    void supervisorApprovalFirstLeavesParentForCenterApproval() {
        TaskEntityImpl parent = parentTask(BpmTaskStatusEnum.RUNNING.getStatus());
        mockNoRemainingChildren(parent);

        ReflectionTestUtils.invokeMethod(service, "handleParentTaskIfSign", "parent-task");

        assertNull(parent.getScopeType());
        assertEquals("233", parent.getAssignee());
        verify(taskService, never()).resolveTask("parent-task");
        verify(taskService, never()).complete("parent-task");
    }

    private TaskEntityImpl parentTask(Integer status) {
        TaskEntityImpl parent = spy(new TaskEntityImpl());
        parent.setId("parent-task"); parent.setAssignee("233"); parent.setProcessInstanceId("process-1");
        parent.setProcessDefinitionId("definition-1"); parent.setTaskDefinitionKey("financeReview");
        parent.setName("财务审批"); parent.setScopeType("parallel");
        if (status != null) {
            doReturn(Map.of(BpmnVariableConstants.TASK_VARIABLE_STATUS, status)).when(parent).getTaskLocalVariables();
        }
        return parent;
    }

    private BpmnModel model() {
        BpmnModel model = new BpmnModel();
        org.flowable.bpmn.model.Process process = new org.flowable.bpmn.model.Process();
        UserTask userTask = new UserTask(); userTask.setId("financeReview");
        process.addFlowElement(userTask); model.addProcess(process);
        return model;
    }

    private void mockNoRemainingChildren(TaskEntityImpl parent) {
        when(managementService.getTableName(TaskEntity.class)).thenReturn("ACT_RU_TASK");
        when(taskService.createNativeTaskQuery()).thenReturn(nativeTaskQuery);
        when(nativeTaskQuery.sql(anyString())).thenReturn(nativeTaskQuery);
        when(nativeTaskQuery.parameter("parentTaskId", "parent-task")).thenReturn(nativeTaskQuery);
        when(nativeTaskQuery.count()).thenReturn(0L);
        when(taskService.createTaskQuery()).thenReturn(taskQuery);
        when(taskQuery.taskId("parent-task")).thenReturn(taskQuery);
        when(taskQuery.includeTaskLocalVariables()).thenReturn(taskQuery);
        when(taskQuery.singleResult()).thenReturn(parent);
    }
}
