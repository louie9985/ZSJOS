package cn.iocoder.yudao.module.bpm.framework.flowable.core.listener;

import cn.iocoder.yudao.framework.audit.ExecutionAuditContext;
import cn.iocoder.yudao.framework.audit.ExecutionAuditContextHolder;
import cn.iocoder.yudao.framework.audit.ExecutionAuditHook;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.bpm.framework.flowable.core.behavior.BpmAuditedAsyncRunnable;
import cn.iocoder.yudao.module.bpm.service.definition.BpmModelService;
import cn.iocoder.yudao.module.bpm.service.message.BpmMessageService;
import cn.iocoder.yudao.module.bpm.service.task.BpmProcessInstanceServiceImpl;
import cn.iocoder.yudao.module.bpm.service.task.BpmTaskServiceImpl;
import org.flowable.common.engine.api.delegate.event.FlowableEngineEventType;
import org.flowable.engine.ProcessEngine;
import org.flowable.engine.impl.cfg.StandaloneInMemProcessEngineConfiguration;
import org.flowable.job.api.Job;
import org.flowable.job.service.impl.asyncexecutor.AbstractAsyncExecutor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class BpmTimerTenantTest {
    private ProcessEngine engine;
    private StandaloneInMemProcessEngineConfiguration config;
    private final BpmMessageService messages = mock(BpmMessageService.class);
    private final List<String> outcomes = new ArrayList<>();

    @BeforeEach
    void setUp() {
        config = new StandaloneInMemProcessEngineConfiguration();
        config.setJdbcUrl("jdbc:h2:mem:timer-tenant-" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1");
        config.setDatabaseSchemaUpdate("create-drop");
        config.setAsyncExecutorActivate(false);
        var hook = new ExecutionAuditHook() {
            @Override
            public void onSuccess(ExecutionAuditContext context, long duration) {
                record(context, "SUCCESS");
            }
            @Override
            public void onFailure(ExecutionAuditContext context, long duration, Throwable error) {
                record(context, "FAILURE");
            }
            private void record(ExecutionAuditContext context, String status) {
                outcomes.add(status + ":" + context.tenantId() + ":" + TenantContextHolder.getTenantId()
                        + ":" + TenantContextHolder.isIgnore());
            }
        };
        config.setAsyncExecutorExecuteAsyncRunnableFactory((job, service) -> new BpmAuditedAsyncRunnable(
                job, service, (AbstractAsyncExecutor) config.getAsyncExecutor(), List.of(hook)));
        engine = config.buildProcessEngine();
        var instances = new BpmProcessInstanceServiceImpl();
        ReflectionTestUtils.setField(instances, "runtimeService", engine.getRuntimeService());
        var tasks = new BpmTaskServiceImpl();
        ReflectionTestUtils.setField(tasks, "processInstanceService", instances);
        ReflectionTestUtils.setField(tasks, "taskService", engine.getTaskService());
        ReflectionTestUtils.setField(tasks, "messageService", messages);
        var modelService = mock(BpmModelService.class);
        when(modelService.getBpmnModelByDefinitionId(anyString())).thenAnswer(call ->
                engine.getRepositoryService().getBpmnModel(call.getArgument(0)));
        var listener = new BpmTaskEventListener();
        ReflectionTestUtils.setField(listener, "modelService", modelService);
        ReflectionTestUtils.setField(listener, "taskService", tasks);
        engine.getRuntimeService().addEventListener(listener, FlowableEngineEventType.TIMER_FIRED);
        String xml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL"
                  xmlns:flowable="http://flowable.org/bpmn" targetNamespace="timer-test">
                  <process id="timerTest" isExecutable="true">
                    <startEvent id="start"/>
                    <sequenceFlow id="f1" sourceRef="start" targetRef="review"/>
                    <userTask id="review" name="Review" flowable:assignee="10"/>
                    <sequenceFlow id="f2" sourceRef="review" targetRef="end"/>
                    <endEvent id="end"/>
                    <boundaryEvent id="reminder" attachedToRef="review" cancelActivity="false">
                      <extensionElements>
                        <flowable:boundaryEventType>1</flowable:boundaryEventType>
                        <flowable:timeoutHandlerType>1</flowable:timeoutHandlerType>
                      </extensionElements>
                      <timerEventDefinition><timeDuration>PT1S</timeDuration></timerEventDefinition>
                    </boundaryEvent>
                  </process>
                </definitions>
                """;
        for (String tenant : List.of("1", "2")) {
            engine.getRepositoryService().createDeployment().tenantId(tenant)
                    .addString("timer-test.bpmn20.xml", xml).deploy();
        }
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
        ExecutionAuditContextHolder.set(null);
        if (engine != null) engine.close();
    }

    @Test
    void realTimerRestoresTenantBeforeInstanceLookupWithoutAsyncWrapper() {
        doAnswer(call -> {
            assertEquals(1L, TenantContextHolder.getRequiredTenantId());
            assertFalse(TenantContextHolder.isIgnore());
            return null;
        }).when(messages).sendMessageWhenTaskTimeout(any());
        Job job = executableJob("1");
        TenantContextHolder.clear();
        engine.getManagementService().executeJob(job.getId());
        verify(messages).sendMessageWhenTaskTimeout(any());
        assertNull(TenantContextHolder.getTenantId());
    }

    @Test
    void asyncJobsAuditEachTenantAndRestoreWorkerContext() {
        var parent = new ExecutionAuditContext("TEST", "parent", null, null, null, null, "99", null);
        TenantContextHolder.setTenantId(99L);
        TenantContextHolder.setIgnore(true);
        ExecutionAuditContextHolder.set(parent);
        for (String tenant : List.of("1", "2")) {
            runJob(executableJob(tenant));
            assertEquals(99L, TenantContextHolder.getTenantId());
            assertTrue(TenantContextHolder.isIgnore());
            assertSame(parent, ExecutionAuditContextHolder.get());
        }
        verify(messages, times(2)).sendMessageWhenTaskTimeout(any());
        assertEquals(List.of("SUCCESS:1:1:false", "SUCCESS:2:2:false"), outcomes);
    }

    @Test
    void failedReminderAuditsFailureAndRetainsFlowableRetry() {
        doThrow(new IllegalStateException("synthetic reminder failure"))
                .when(messages).sendMessageWhenTaskTimeout(any());
        Job job = executableJob("2");
        TenantContextHolder.clear();
        runJob(job);
        assertEquals(List.of("FAILURE:2:2:false"), outcomes);
        assertNull(TenantContextHolder.getTenantId());
        assertNull(ExecutionAuditContextHolder.get());
        var retry = engine.getManagementService().createTimerJobQuery().jobId(job.getId()).singleResult();
        assertNotNull(retry);
        assertEquals(job.getRetries() - 1, retry.getRetries());
        assertNotNull(engine.getRuntimeService().createProcessInstanceQuery()
                .processInstanceId(job.getProcessInstanceId()).singleResult());
    }

    @Test
    void completedTaskRemovesItsTimerInsteadOfSendingAnObsoleteReminder() {
        String instance = engine.getRuntimeService().startProcessInstanceByKeyAndTenantId("timerTest", "1").getId();
        var task = engine.getTaskService().createTaskQuery().processInstanceId(instance).singleResult();
        engine.getTaskService().complete(task.getId());
        assertEquals(0, engine.getManagementService().createTimerJobQuery().processInstanceId(instance).count());
        verifyNoInteractions(messages);
    }

    private Job executableJob(String tenant) {
        String instance = engine.getRuntimeService().startProcessInstanceByKeyAndTenantId("timerTest", tenant).getId();
        var timer = engine.getManagementService().createTimerJobQuery().processInstanceId(instance).singleResult();
        engine.getManagementService().moveTimerToExecutableJob(timer.getId());
        return engine.getManagementService().createJobQuery().jobId(timer.getId()).singleResult();
    }

    private void runJob(Job job) {
        config.getAsyncExecutorExecuteAsyncRunnableFactory()
                .createExecuteAsyncRunnable(job, config.getJobServiceConfiguration()).run();
    }
}
