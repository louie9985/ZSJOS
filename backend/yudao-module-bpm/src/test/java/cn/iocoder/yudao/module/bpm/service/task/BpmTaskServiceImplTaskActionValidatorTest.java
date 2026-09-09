package cn.iocoder.yudao.module.bpm.service.task;

import cn.iocoder.yudao.framework.test.core.ut.BaseMockitoUnitTest;
import cn.iocoder.yudao.module.bpm.api.task.BpmTaskActionValidator;
import cn.iocoder.yudao.module.bpm.api.task.dto.BpmTaskActionContext;
import cn.iocoder.yudao.module.bpm.controller.admin.task.vo.task.BpmTaskRejectReqVO;
import cn.iocoder.yudao.module.bpm.service.comment.BpmCommentService;
import cn.iocoder.yudao.module.bpm.service.definition.BpmProcessDefinitionService;
import org.flowable.engine.TaskService;
import org.flowable.engine.runtime.ProcessInstance;
import org.flowable.task.api.TaskQuery;
import org.flowable.task.service.impl.persistence.entity.TaskEntityImpl;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.beans.factory.ObjectProvider;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BpmTaskServiceImplTaskActionValidatorTest extends BaseMockitoUnitTest {

    @InjectMocks
    private BpmTaskServiceImpl service;
    @Mock
    private TaskService taskService;
    @Mock
    private TaskQuery taskQuery;
    @Mock
    private BpmProcessInstanceService processInstanceService;
    @Mock
    private BpmProcessDefinitionService bpmProcessDefinitionService;
    @Mock
    private BpmCommentService commentService;
    @Mock
    private ObjectProvider<BpmTaskActionValidator> taskActionValidatorProvider;
    @Mock
    private BpmTaskActionValidator validator;

    @Test
    void rejectStopsBeforeAnyTaskMutationWhenBusinessValidationFails() {
        TaskEntityImpl task = new TaskEntityImpl();
        task.setId("task-1");
        task.setAssignee("7");
        task.setProcessInstanceId("process-1");
        task.setProcessDefinitionId("definition-1");
        task.setTaskDefinitionKey("directorReview");
        task.setTenantId("11");
        ProcessInstance instance = org.mockito.Mockito.mock(ProcessInstance.class);
        when(instance.getProcessDefinitionKey()).thenReturn("content-review");
        when(instance.getBusinessKey()).thenReturn("content-review-batch:99");
        when(taskService.createTaskQuery()).thenReturn(taskQuery);
        when(taskQuery.taskId("task-1")).thenReturn(taskQuery);
        when(taskQuery.taskTenantId(nullable(String.class))).thenReturn(taskQuery);
        when(taskQuery.includeTaskLocalVariables()).thenReturn(taskQuery);
        when(taskQuery.singleResult()).thenReturn(task);
        when(processInstanceService.getProcessInstance("process-1")).thenReturn(instance);
        when(taskActionValidatorProvider.orderedStream()).thenAnswer(invocation -> Stream.of(validator));
        RuntimeException failure = new RuntimeException("business validation failed");
        org.mockito.Mockito.doThrow(failure).when(validator).validate(any());

        RuntimeException actual = assertThrows(RuntimeException.class,
                () -> service.rejectTask(7L, new BpmTaskRejectReqVO().setId("task-1").setReason("reject")));

        assertEquals(failure, actual);
        ArgumentCaptor<BpmTaskActionContext> context = ArgumentCaptor.forClass(BpmTaskActionContext.class);
        verify(validator).validate(context.capture());
        assertEquals(BpmTaskActionValidator.ACTION_REJECT, context.getValue().getAction());
        assertEquals("content-review-batch:99", context.getValue().getBusinessKey());
        assertEquals("directorReview", context.getValue().getTaskDefinitionKey());
        verify(taskService, never()).setVariableLocal(any(), any(), any());
        verify(taskService, never()).complete(any(String.class));
        verify(commentService, never()).createComment(any(), any(), any(), any(String.class));
    }

}
