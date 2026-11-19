package cn.iocoder.yudao.module.bpm.service.task;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.bpm.enums.task.BpmTaskStatusEnum;
import cn.iocoder.yudao.module.bpm.service.comment.BpmCommentService;
import cn.iocoder.yudao.module.bpm.framework.flowable.core.enums.BpmnVariableConstants;
import org.flowable.engine.ProcessEngineConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class BpmObsoleteNodeSkipEngineTest {
    @Test
    void obsoleteBranchRunsOriginalJoinAndPreservesFinanceAndActiveSign() {
        var engine = ProcessEngineConfiguration.createStandaloneInMemProcessEngineConfiguration()
                .setJdbcUrl("jdbc:h2:mem:obsolete_node_skip;DB_CLOSE_DELAY=-1")
                .setDatabaseSchemaUpdate("true").setAsyncExecutorActivate(false).buildProcessEngine();
        try {
            TenantContextHolder.setTenantId(1L);
            var tasks = engine.getTaskService();
            var runtime = engine.getRuntimeService();
            var service = new BpmTaskServiceImpl();
            ReflectionTestUtils.setField(service, "taskService", tasks);
            var instances = mock(BpmProcessInstanceService.class);
            when(instances.getProcessInstance(anyString()))
                    .thenAnswer(call -> runtime.createProcessInstanceQuery().processInstanceId(call.getArgument(0)).singleResult());
            ReflectionTestUtils.setField(service, "processInstanceService", instances);
            var comments = mock(BpmCommentService.class);
            ReflectionTestUtils.setField(service, "commentService", comments);
            engine.getRepositoryService().createDeployment().tenantId("1").addString("legacy.bpmn20.xml", """
                <?xml version="1.0" encoding="UTF-8"?>
                <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL" targetNamespace="test">
                  <process id="legacy" isExecutable="true">
                    <startEvent id="start"/><sequenceFlow id="s1" sourceRef="start" targetRef="fork"/>
                    <parallelGateway id="fork"/>
                    <sequenceFlow id="s2" sourceRef="fork" targetRef="registration"/>
                    <sequenceFlow id="s3" sourceRef="fork" targetRef="finance"/>
                    <userTask id="registration"/><userTask id="finance"/>
                    <sequenceFlow id="s4" sourceRef="registration" targetRef="join"/>
                    <sequenceFlow id="s5" sourceRef="finance" targetRef="join"/>
                    <parallelGateway id="join"/><sequenceFlow id="s6" sourceRef="join" targetRef="end"/>
                    <endEvent id="end"/>
                  </process>
                </definitions>
                """).deploy();

            for (boolean financeAlreadyApproved : new boolean[] {false, true}) {
                var instance = runtime.startProcessInstanceByKeyAndTenantId("legacy", "sales-order:42", "1");
                String id = instance.getId();
                var registration = tasks.createTaskQuery().processInstanceId(id).taskDefinitionKey("registration").singleResult();
                var finance = tasks.createTaskQuery().processInstanceId(id).taskDefinitionKey("finance").singleResult();
                tasks.setVariableLocal(finance.getId(), BpmnVariableConstants.TASK_VARIABLE_STATUS,
                        BpmTaskStatusEnum.APPROVE.getStatus());
                if (financeAlreadyApproved) tasks.complete(finance.getId());
                service.skipObsoleteNode(100L, id, "legacy", "sales-order:42", "registration", "retire registration review");

                var historical = engine.getHistoryService().createHistoricTaskInstanceQuery()
                        .taskId(registration.getId()).includeTaskLocalVariables().singleResult();
                assertEquals(BpmTaskStatusEnum.SKIP.getStatus(),
                        historical.getTaskLocalVariables().get(BpmnVariableConstants.TASK_VARIABLE_STATUS));
                verify(comments).createComment(registration.getId(), id,
                        cn.iocoder.yudao.module.bpm.enums.task.BpmCommentTypeEnum.MIGRATION_SKIP, 100L, "retire registration review");
                if (!financeAlreadyApproved) {
                    assertNotNull(runtime.createProcessInstanceQuery().processInstanceId(id).singleResult());
                    assertEquals(finance.getId(), tasks.createTaskQuery().processInstanceId(id).singleResult().getId());
                    tasks.complete(finance.getId());
                }
                assertNotNull(engine.getHistoryService().createHistoricProcessInstanceQuery()
                        .processInstanceId(id).singleResult().getEndTime());
            }
            var signedInstance = runtime.startProcessInstanceByKeyAndTenantId("legacy", "sales-order:43", "1");
            var signedRegistration = (org.flowable.task.service.impl.persistence.entity.TaskEntity) tasks.createTaskQuery()
                    .processInstanceId(signedInstance.getId()).taskDefinitionKey("registration").singleResult();
            signedRegistration.setScopeType("parallel");
            tasks.saveTask(signedRegistration);
            assertThrows(ServiceException.class, () -> service.skipObsoleteNode(100L, signedInstance.getId(),
                    "legacy", "sales-order:43", "registration", "retire registration review"));
            assertNotNull(tasks.createTaskQuery().taskId(signedRegistration.getId()).singleResult());
        } finally {
            TenantContextHolder.clear();
            engine.close();
        }
    }
}
