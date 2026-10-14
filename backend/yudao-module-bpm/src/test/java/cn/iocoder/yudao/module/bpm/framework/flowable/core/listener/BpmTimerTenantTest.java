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
import org.flowable.spring.SpringProcessEngineConfiguration;
import org.flowable.spring.job.service.SpringAsyncExecutor;
import cn.iocoder.yudao.module.bpm.framework.flowable.config.BpmFlowableConfiguration;
import cn.iocoder.yudao.module.bpm.framework.flowable.core.behavior.BpmActivityBehaviorFactory;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.flowable.job.service.impl.persistence.entity.JobEntity;
import java.util.concurrent.CopyOnWriteArrayList;
import org.flowable.job.api.Job;
import org.flowable.job.service.impl.asyncexecutor.AbstractAsyncExecutor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class BpmTimerTenantTest {
    private ProcessEngine engine;
    private org.springframework.context.annotation.AnnotationConfigApplicationContext spring;
    private SpringProcessEngineConfiguration config;
    private final BpmMessageService messages = mock(BpmMessageService.class);
    private boolean failAudit;
    private final List<String> outcomes = new CopyOnWriteArrayList<>();

    @BeforeEach
    void setUp() {
        config = new SpringProcessEngineConfiguration();
        var dataSource = new DriverManagerDataSource("jdbc:h2:mem:timer-tenant-" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1", "sa", "");
        config.setDataSource(dataSource);
        config.setTransactionManager(new DataSourceTransactionManager(dataSource));
        config.setAsyncExecutor(new SpringAsyncExecutor());
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
                if (failAudit) throw new IllegalStateException("synthetic audit persistence failure");
            }
        };
        spring = new org.springframework.context.annotation.AnnotationConfigApplicationContext();
        spring.registerBean("auditHook", ExecutionAuditHook.class, () -> hook);
        spring.registerBean("applicationTaskExecutor", org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor.class,
                () -> (org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor) new BpmFlowableConfiguration().taskExecutor());
        spring.register(cn.iocoder.yudao.framework.quartz.config.YudaoAsyncAutoConfiguration.class);
        spring.refresh();
        config.setAsyncTaskExecutor(new org.flowable.common.spring.async.SpringAsyncTaskExecutor(
                spring.getBean("applicationTaskExecutor", org.springframework.core.task.AsyncTaskExecutor.class)));
        var beans = spring.getDefaultListableBeanFactory();
        var candidates = mock(cn.iocoder.yudao.module.bpm.framework.flowable.core.candidate.BpmTaskCandidateInvoker.class);
        when(candidates.calculateUsersByTask(any())).thenReturn(java.util.Set.of(10L));
        var behavior = new BpmActivityBehaviorFactory();
        behavior.setTaskCandidateInvoker(candidates);
        new BpmFlowableConfiguration().bpmProcessEngineConfigurationConfigurer(
                beans.getBeanProvider(org.flowable.common.engine.api.delegate.event.FlowableEventListener.class),
                beans.getBeanProvider(org.flowable.common.engine.api.delegate.FlowableFunctionDelegate.class),
                behavior, beans.getBeanProvider(ExecutionAuditHook.class)).configure(config);
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
        if (spring != null) spring.close();
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
        assertTrue(retry.getExceptionMessage().contains("synthetic reminder failure"));
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

    @Test
    void auditFailureDoesNotReplaySuccessfulBusiness() {
        failAudit = true;
        Job job = executableJob("1");
        TenantContextHolder.clear();
        runJob(job);
        verify(messages, times(1)).sendMessageWhenTaskTimeout(any());
        assertEquals(List.of("SUCCESS:1:1:false"), outcomes);
        assertEquals(0, engine.getManagementService().createJobQuery().jobId(job.getId()).count());
        assertEquals(0, engine.getManagementService().createTimerJobQuery().jobId(job.getId()).count());
        assertNull(TenantContextHolder.getTenantId());
        assertNull(ExecutionAuditContextHolder.get());
    }

    @Test
    void invalidTenantRefusesBusinessAndRetainsRetry() {
        for (String invalid : java.util.Arrays.asList(null, "", " ", "invalid", "0", "-1", "9999999999999999999999")) {
            Job job = executableJob("1");
            ((JobEntity) job).setTenantId(invalid);
            TenantContextHolder.setTenantId(99L);
            TenantContextHolder.setIgnore(true);
            runJob(job);
            assertEquals(99L, TenantContextHolder.getTenantId());
            assertTrue(TenantContextHolder.isIgnore());
            var retry = engine.getManagementService().createTimerJobQuery().jobId(job.getId()).singleResult();
            assertNotNull(retry);
            assertEquals(job.getRetries() - 1, retry.getRetries());
        }
        verifyNoInteractions(messages);
        assertEquals(7, outcomes.size());
        assertTrue(outcomes.stream().allMatch(value -> value.startsWith("FAILURE:") && value.endsWith(":null:false")));
    }

    @Test
    void springExecutorAcquiresAndExecutesTimerOnWorker() throws Exception {
        var finished = new java.util.concurrent.CountDownLatch(1);
        doAnswer(call -> {
            assertEquals(1L, TenantContextHolder.getRequiredTenantId());
            assertFalse(TenantContextHolder.isIgnore());
            finished.countDown();
            return null;
        }).when(messages).sendMessageWhenTaskTimeout(any());
        engine.getRuntimeService().startProcessInstanceByKeyAndTenantId("timerTest", "1");
        config.getAsyncExecutor().start();
        try {
            assertTrue(finished.await(20, java.util.concurrent.TimeUnit.SECONDS));
            long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(10);
            while (outcomes.isEmpty() && System.nanoTime() < deadline) Thread.sleep(20);
            assertEquals(List.of("SUCCESS:1:1:false"), outcomes);
            verify(messages, times(1)).sendMessageWhenTaskTimeout(any());
        } finally {
            config.getAsyncExecutor().shutdown();
        }
    }

    private Job executableJob(String tenant) {
        String instance = engine.getRuntimeService().startProcessInstanceByKeyAndTenantId("timerTest", tenant).getId();
        var timer = engine.getManagementService().createTimerJobQuery().processInstanceId(instance).singleResult();
        engine.getManagementService().moveTimerToExecutableJob(timer.getId());
        return engine.getManagementService().createJobQuery().jobId(timer.getId()).singleResult();
    }

    private void runJob(Job job) {
        var runnable = ((AbstractAsyncExecutor) config.getAsyncExecutor()).getExecuteAsyncRunnableFactory()
                .createExecuteAsyncRunnable(job, config.getJobServiceConfiguration());
        assertInstanceOf(BpmAuditedAsyncRunnable.class, runnable);
        runnable.run();
    }
}
