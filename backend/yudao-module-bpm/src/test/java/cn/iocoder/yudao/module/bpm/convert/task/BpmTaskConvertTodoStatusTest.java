package cn.iocoder.yudao.module.bpm.convert.task;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.bpm.controller.admin.task.vo.task.BpmTaskRespVO;
import cn.iocoder.yudao.module.bpm.enums.task.BpmTaskStatusEnum;
import cn.iocoder.yudao.module.bpm.framework.flowable.core.enums.BpmnVariableConstants;
import org.flowable.engine.runtime.ProcessInstance;
import org.flowable.engine.impl.persistence.entity.ExecutionEntityImpl;
import org.flowable.task.api.Task;
import org.flowable.task.service.impl.persistence.entity.TaskEntityImpl;
import org.junit.jupiter.api.Test;

import java.util.Date;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class BpmTaskConvertTodoStatusTest {

    @Test
    void returnsEachTasksActualStatusAlongsideProcessSummary() {
        Task running = task("task-1", BpmTaskStatusEnum.RUNNING.getStatus());
        Task waiting = task("task-2", BpmTaskStatusEnum.WAIT.getStatus());
        ProcessInstance process = mock(ExecutionEntityImpl.class);
        when(process.getId()).thenReturn("process-1");
        when(process.getName()).thenReturn("审批测试");
        when(process.getStartUserId()).thenReturn("1");
        when(process.getProcessDefinitionId()).thenReturn("opaque-id");
        when(process.getProcessDefinitionKey()).thenReturn("zsjos_viral_content_review");
        when(process.getStartTime()).thenReturn(new Date(0));
        when(process.getProcessVariables()).thenReturn(Map.of());

        PageResult<BpmTaskRespVO> result = BpmTaskConvert.INSTANCE.buildTodoTaskPage(
                new PageResult<>(List.of(running, waiting), 12L), Map.of("process-1", process), Map.of(), Map.of());

        assertEquals(12L, result.getTotal());
        assertEquals(List.of("task-1", "task-2"), result.getList().stream().map(BpmTaskRespVO::getId).toList());
        assertEquals(BpmTaskStatusEnum.RUNNING.getStatus(), result.getList().get(0).getStatus());
        assertEquals(BpmTaskStatusEnum.WAIT.getStatus(), result.getList().get(1).getStatus());
        assertEquals("审批测试", result.getList().getFirst().getProcessInstance().getName());
        assertEquals("zsjos_viral_content_review",result.getList().getFirst().getProcessDefinitionKey());
        assertEquals("zsjos_viral_content_review",result.getList().getFirst().getProcessInstance().getProcessDefinitionKey());
    }

    @Test
    void preservesTaskStatusWhenProcessSummaryIsMissing() {
        var result = BpmTaskConvert.INSTANCE.buildTodoTaskPage(
                new PageResult<>(List.of(task("task-1", BpmTaskStatusEnum.RUNNING.getStatus())), 1L),
                Map.of(), Map.of(), Map.of());

        assertEquals(BpmTaskStatusEnum.RUNNING.getStatus(), result.getList().getFirst().getStatus());
        assertNull(result.getList().getFirst().getProcessInstance());
    }

    @Test
    void doesNotInventStatusWhenVariableIsMissing() {
        var result = BpmTaskConvert.INSTANCE.buildTodoTaskPage(
                new PageResult<>(List.of(task("task-1", null)), 1L), Map.of(), Map.of(), Map.of());

        assertNull(result.getList().getFirst().getStatus());
    }

    @Test
    void preservesEmptyPage() {
        var result = BpmTaskConvert.INSTANCE.buildTodoTaskPage(PageResult.empty(), Map.of(), Map.of(), Map.of());

        assertTrue(result.getList().isEmpty());
        assertEquals(0L, result.getTotal());
    }

    private Task task(String id, Integer status) {
        Task task = mock(TaskEntityImpl.class);
        when(task.getId()).thenReturn(id);
        when(task.getProcessInstanceId()).thenReturn("process-1");
        when(task.getTaskLocalVariables()).thenReturn(status == null ? Map.of()
                : Map.of(BpmnVariableConstants.TASK_VARIABLE_STATUS, status));
        return task;
    }
    @Test void historicTasksExposeActualDefinitionKeyWithoutParsingOpaqueIds() {
        var task=mock(org.flowable.task.service.impl.persistence.entity.HistoricTaskInstanceEntityImpl.class);
        when(task.getId()).thenReturn("done-task");when(task.getProcessInstanceId()).thenReturn("historic");
        when(task.getAssignee()).thenReturn("9");
        when(task.getTaskLocalVariables()).thenReturn(Map.of());
        var instance=mock(org.flowable.engine.impl.persistence.entity.HistoricProcessInstanceEntityImpl.class);
        when(instance.getProcessDefinitionId()).thenReturn("opaque-id");
        when(instance.getStartUserId()).thenReturn("1");
        when(instance.getProcessDefinitionKey()).thenReturn("zsjos_viral_account_review");
        when(instance.getProcessVariables()).thenReturn(Map.of());
        var result=BpmTaskConvert.INSTANCE.buildTaskPage(new PageResult<>(List.of(task),1L),
                Map.of("historic",instance),Map.of(),Map.of(),Map.of(),Map.of());
        assertEquals("historic",result.getList().getFirst().getProcessInstanceId());
        assertEquals("zsjos_viral_account_review",result.getList().getFirst().getProcessDefinitionKey());
        assertEquals("zsjos_viral_account_review",result.getList().getFirst().getProcessInstance().getProcessDefinitionKey());
    }

}
