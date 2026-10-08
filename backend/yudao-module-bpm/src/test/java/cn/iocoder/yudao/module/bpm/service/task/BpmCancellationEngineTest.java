package cn.iocoder.yudao.module.bpm.service.task;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.bpm.framework.flowable.core.listener.BpmTaskEventListener;
import org.flowable.engine.ProcessEngineConfiguration;
import org.flowable.common.engine.api.delegate.event.FlowableEngineEventType;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import static org.junit.jupiter.api.Assertions.*;

/** Real engine evidence for cancellation ordering; does not replace deployed business callback acceptance. */
class BpmCancellationEngineTest {
    @Test void cancellationAndReturnCleanParallelTasksWithProductionCancellationListener() {
        var engine = ProcessEngineConfiguration.createStandaloneInMemProcessEngineConfiguration()
                .setJdbcUrl("jdbc:h2:mem:runtime_cancellation;DB_CLOSE_DELAY=-1")
                .setDatabaseSchemaUpdate("true").setAsyncExecutorActivate(false).buildProcessEngine();
        try {
            TenantContextHolder.setTenantId(1L);
            var service = new BpmTaskServiceImpl();
            ReflectionTestUtils.setField(service, "taskService", engine.getTaskService());
            ReflectionTestUtils.setField(service, "historyService", engine.getHistoryService());
            ReflectionTestUtils.setField(service, "runtimeService", engine.getRuntimeService());
            var models = org.mockito.Mockito.mock(cn.iocoder.yudao.module.bpm.service.definition.BpmModelService.class);
            org.mockito.Mockito.when(models.getBpmnModelByDefinitionId(org.mockito.ArgumentMatchers.anyString()))
                    .thenAnswer(call -> engine.getRepositoryService().getBpmnModel(call.getArgument(0)));
            ReflectionTestUtils.setField(service, "modelService", models);
            var listener = new BpmTaskEventListener(); ReflectionTestUtils.setField(listener, "taskService", service);
            engine.getRuntimeService().addEventListener(listener, FlowableEngineEventType.ACTIVITY_CANCELLED);
            engine.getRepositoryService().createDeployment().tenantId("1").addString("parallel.bpmn20.xml", """
                <?xml version="1.0" encoding="UTF-8"?>
                <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL" targetNamespace="test">
                  <process id="parallel" isExecutable="true">
                    <startEvent id="start"/><sequenceFlow id="s1" sourceRef="start" targetRef="draft"/>
                    <userTask id="draft"/><sequenceFlow id="s2" sourceRef="draft" targetRef="fork"/>
                    <parallelGateway id="fork"/><sequenceFlow id="s3" sourceRef="fork" targetRef="a"/>
                    <sequenceFlow id="s4" sourceRef="fork" targetRef="b"/><userTask id="a"/><userTask id="b"/>
                    <sequenceFlow id="s5" sourceRef="a" targetRef="join"/><sequenceFlow id="s6" sourceRef="b" targetRef="join"/>
                    <parallelGateway id="join"/><sequenceFlow id="s7" sourceRef="join" targetRef="end"/><endEvent id="end"/>
                  </process>
                </definitions>
                """).deploy();
            for (boolean returnFirst : new boolean[] {false, true}) {
                var instance = engine.getRuntimeService().startProcessInstanceByKeyAndTenantId("parallel", "1");
                engine.getTaskService().complete(engine.getTaskService().createTaskQuery().processInstanceId(instance.getId()).singleResult().getId());
                var tasks = engine.getTaskService().createTaskQuery().processInstanceId(instance.getId()).list();
                assertEquals(2, tasks.size());
                // Duplicate terminal cancellation must be harmless before engine cleanup.
                service.processTaskCanceled(tasks.getFirst().getId()); service.processTaskCanceled(tasks.getFirst().getId());
                if (returnFirst) {
                    // returnTask marks every affected task before moving executions; reproduce that boundary.
                    tasks.forEach(task -> service.processTaskCanceled(task.getId()));
                    engine.getRuntimeService().createChangeActivityStateBuilder().processInstanceId(instance.getId())
                            .moveActivityIdsToSingleActivityId(java.util.List.of("a", "b"), "draft").changeState();
                    assertEquals("draft", engine.getTaskService().createTaskQuery().processInstanceId(instance.getId()).singleResult().getTaskDefinitionKey());
                }
                service.moveTaskToEnd(instance.getId(), "test cancellation");
                for (var task : tasks) {
                    var historical = engine.getHistoryService().createHistoricTaskInstanceQuery().taskId(task.getId()).includeTaskLocalVariables().singleResult();
                    assertEquals(cn.iocoder.yudao.module.bpm.enums.task.BpmTaskStatusEnum.CANCEL.getStatus(),
                            historical.getTaskLocalVariables().get(cn.iocoder.yudao.module.bpm.framework.flowable.core.enums.BpmnVariableConstants.TASK_VARIABLE_STATUS));
                }
                assertEquals(0, engine.getTaskService().createTaskQuery().processInstanceId(instance.getId()).count());
                assertNotNull(engine.getHistoryService().createHistoricProcessInstanceQuery().processInstanceId(instance.getId()).singleResult().getEndTime());
            }
        } finally { TenantContextHolder.clear(); engine.close(); }
    }
}
