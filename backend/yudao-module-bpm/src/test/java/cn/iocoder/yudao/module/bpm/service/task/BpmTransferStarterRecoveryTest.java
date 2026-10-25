package cn.iocoder.yudao.module.bpm.service.task;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.bpm.controller.admin.definition.vo.model.BpmModelSaveReqVO;
import cn.iocoder.yudao.module.bpm.framework.flowable.core.behavior.BpmActivityBehaviorFactory;
import cn.iocoder.yudao.module.bpm.framework.flowable.core.candidate.BpmTaskCandidateInvoker;
import cn.iocoder.yudao.module.bpm.framework.flowable.core.candidate.strategy.dept.BpmTaskCandidateStartUserSelectStrategy;
import cn.iocoder.yudao.module.bpm.framework.flowable.core.candidate.strategy.other.BpmTaskCandidateExpressionStrategy;
import cn.iocoder.yudao.module.bpm.framework.flowable.core.util.SimpleModelUtils;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import cn.iocoder.yudao.module.system.api.user.dto.AdminUserRespDTO;
import org.flowable.engine.ProcessEngine;
import org.flowable.spring.SpringProcessEngineConfiguration;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.flowable.engine.delegate.DelegateExecution;
import org.junit.jupiter.api.*;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.*;

import static cn.iocoder.yudao.module.bpm.framework.flowable.core.enums.BpmnVariableConstants.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class BpmTransferStarterRecoveryTest {
    private ProcessEngine engine;
    private BpmStartUserTaskRecovery recovery;
    private BpmStartUserTaskRecovery.Request request;
    private BpmTaskCandidateInvoker invoker;
    private PermissionApi permissions;
    private DataSourceTransactionManager transactionManager;

    @BeforeEach
    void setup() throws Exception {
        TenantContextHolder.setTenantId(1L);
        invoker = mock(BpmTaskCandidateInvoker.class);
        when(invoker.calculateUsersByTask(any())).thenAnswer(call -> {
            DelegateExecution execution = call.getArgument(0);
            return "StartUserNode".equals(execution.getCurrentActivityId()) ? Set.of(40L)
                    : new BpmTaskCandidateExpressionStrategy().calculateUsersByTask(execution, "${coll_userList}");
        });
        var factory = new BpmActivityBehaviorFactory(); factory.setTaskCandidateInvoker(invoker);
        var dataSource = new DriverManagerDataSource("jdbc:h2:mem:starter-recovery-" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1", "sa", "");
        transactionManager = new DataSourceTransactionManager(dataSource);
        var config = new SpringProcessEngineConfiguration();
        config.setDataSource(dataSource); config.setTransactionManager(transactionManager);
        config.setDatabaseSchemaUpdate("create-drop"); config.setAsyncExecutorActivate(false);
        config.setActivityBehaviorFactory(factory);
        engine = config.buildProcessEngine();
        Path root = Path.of("").toAbsolutePath();
        while (root != null && !Files.exists(root.resolve("script/bpm/manifest.json"))) root = root.getParent();
        var asset = JsonUtils.parseObject(Files.readString(Objects.requireNonNull(root).resolve(
                "script/bpm/zsjos_lead_transfer_request/2.0.0/process-model.json")), BpmModelSaveReqVO.class);
        asset.getSimpleModel().getChildNode().setCandidateStrategy(60).setCandidateParam("${coll_userList}");
        var model = SimpleModelUtils.buildBpmnModel(asset.getKey(), asset.getName(), asset.getSimpleModel());
        var deployment = engine.getRepositoryService().createDeployment().tenantId("1")
                .addBpmnModel(asset.getKey() + ".bpmn20.xml", model).deploy();
        var definition = engine.getRepositoryService().createProcessDefinitionQuery().deploymentId(deployment.getId()).singleResult();
        var instance = engine.getRuntimeService().createProcessInstanceBuilder().processDefinitionId(definition.getId())
                .businessKey("lead-transfer:fixture").variables(Map.of(PROCESS_INSTANCE_VARIABLE_START_USER_SELECT_ASSIGNEES,
                        Map.of("ownerManagerReview", List.of(21L)), PROCESS_INSTANCE_VARIABLE_START_USER_ID, 40L)).start();
        var starter = engine.getTaskService().createTaskQuery().processInstanceId(instance.getId()).singleResult();
        engine.getTaskService().setVariableLocal(starter.getId(), TASK_VARIABLE_STATUS, 2);
        engine.getTaskService().setVariableLocal(starter.getId(), "bpm_action_actor_snapshot", Map.of("subjectType", "SYSTEM"));
        engine.getTaskService().addComment(starter.getId(), instance.getId(), "Original automatic approval");
        assertThrows(RuntimeException.class, () -> engine.getTaskService().complete(starter.getId()));
        String digest;
        try (var xml = engine.getRepositoryService().getProcessModel(definition.getId())) {
            digest = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(xml.readAllBytes()));
        }
        var users = mock(AdminUserApi.class);
        when(users.getUser(anyLong())).thenAnswer(call -> new AdminUserRespDTO().setId(call.getArgument(0)).setStatus(0));
        permissions = mock(PermissionApi.class);
        when(permissions.hasAnyPermissions(anyLong(), any(String[].class))).thenReturn(true);
        recovery = new BpmStartUserTaskRecovery(engine, users, permissions, transactionManager);
        request = new BpmStartUserTaskRecovery.Request("1", instance.getId(), definition.getId(), "lead-transfer:fixture",
                starter.getId(), "ownerManagerReview", "coll_userList", digest, List.of(21L),
                "bpm:task:update", 99L, "isolated recovery fixture");
    }

    @AfterEach
    void close() {
        TenantContextHolder.clear();
        if (engine != null) engine.close();
    }

    @Test
    void recoversOriginalInstancePreservesAuditAndRepeatsWithoutMutation() {
        engine.getRepositoryService().suspendProcessDefinitionById(request.definitionId(), false, null);
        assertEquals("READY", recovery.inspect(request));
        assertFalse(engine.getRuntimeService().hasVariable(request.instanceId(), "coll_userList"));
        assertEquals("RECOVERED", recovery.recover(request));
        var tasks = engine.getTaskService().createTaskQuery().processInstanceId(request.instanceId()).list();
        assertEquals(1, tasks.size()); assertEquals("21", tasks.getFirst().getAssignee());
        assertEquals("ownerManagerReview", tasks.getFirst().getTaskDefinitionKey());
        var original = engine.getHistoryService().createHistoricTaskInstanceQuery().taskId(request.startTaskId())
                .includeTaskLocalVariables().singleResult();
        assertNotNull(original.getEndTime()); assertEquals(2, original.getTaskLocalVariables().get(TASK_VARIABLE_STATUS));
        assertEquals(Map.of("subjectType", "SYSTEM"), original.getTaskLocalVariables().get("bpm_action_actor_snapshot"));
        assertEquals(2, engine.getTaskService().getProcessInstanceComments(request.instanceId()).size());
        assertEquals("ALREADY_RECOVERED", recovery.recover(request));
        assertEquals(2, engine.getTaskService().getProcessInstanceComments(request.instanceId()).size());
        assertEquals(tasks.getFirst().getId(), engine.getTaskService().createTaskQuery()
                .processInstanceId(request.instanceId()).singleResult().getId());
    }

    @Test
    void correctedModelAdvancesStarterToSelectedReviewerWithoutLegacyVariable() throws Exception {
        var instances = mock(BpmProcessInstanceService.class);
        when(instances.getProcessInstance(anyString())).thenAnswer(call -> engine.getRuntimeService()
                .createProcessInstanceQuery().processInstanceId(call.getArgument(0))
                .includeProcessVariables().singleResult());
        var selected = new BpmTaskCandidateStartUserSelectStrategy();
        ReflectionTestUtils.setField(selected, "processInstanceService", instances);
        doAnswer(call -> {
            DelegateExecution execution = call.getArgument(0);
            if ("StartUserNode".equals(execution.getCurrentActivityId())) return Set.of(40L);
            assertEquals(35, cn.iocoder.yudao.module.bpm.framework.flowable.core.util.BpmnModelUtils
                    .parseCandidateStrategy(execution.getCurrentFlowElement()));
            return selected.calculateUsersByTask(execution, null);
        }).when(invoker).calculateUsersByTask(any());
        Path root = Path.of("").toAbsolutePath();
        while (root != null && !Files.exists(root.resolve("script/bpm/manifest.json"))) root = root.getParent();
        var asset = JsonUtils.parseObject(Files.readString(Objects.requireNonNull(root).resolve(
                "script/bpm/zsjos_lead_transfer_request/2.0.0/process-model.json")), BpmModelSaveReqVO.class);
        var deployment = engine.getRepositoryService().createDeployment().tenantId("1")
                .addBpmnModel(asset.getKey() + ".bpmn20.xml", SimpleModelUtils.buildBpmnModel(
                        asset.getKey(), asset.getName(), asset.getSimpleModel())).deploy();
        var definition = engine.getRepositoryService().createProcessDefinitionQuery()
                .deploymentId(deployment.getId()).singleResult();
        var instance = engine.getRuntimeService().createProcessInstanceBuilder().processDefinitionId(definition.getId())
                .businessKey("lead-transfer:corrected-fixture").variables(Map.of(
                        PROCESS_INSTANCE_VARIABLE_START_USER_SELECT_ASSIGNEES, Map.of("ownerManagerReview", List.of(21L)),
                        PROCESS_INSTANCE_VARIABLE_START_USER_ID, 40L)).start();
        var starter = engine.getTaskService().createTaskQuery().processInstanceId(instance.getId()).singleResult();
        assertEquals("StartUserNode", starter.getTaskDefinitionKey());
        // The automatic starter handler completes this same engine task; the real
        // selection strategy must consume the business-supplied map at the next node.
        automaticCallback(starter.getId());
        var task = engine.getTaskService().createTaskQuery().processInstanceId(instance.getId()).singleResult();
        assertEquals("ownerManagerReview", task.getTaskDefinitionKey());
        assertEquals("21", task.getAssignee());
        assertFalse(engine.getRuntimeService().hasVariable(instance.getId(), "coll_userList"));
        assertNotNull(engine.getHistoryService().createHistoricTaskInstanceQuery()
                .taskId(starter.getId()).singleResult().getEndTime());
        assertNotNull(engine.getTaskService().createTaskQuery().taskId(request.startTaskId()).singleResult(),
                "Publishing a corrected model must not silently change an old instance");
    }

    @Test
    void rejectsTenantPermissionAndChangedSnapshotWithoutWrites() {
        TenantContextHolder.setTenantId(2L);
        assertThrows(IllegalStateException.class, () -> recovery.recover(request));
        TenantContextHolder.setTenantId(1L);
        when(permissions.hasAnyPermissions(99L, "bpm:task:update")).thenReturn(false);
        assertThrows(IllegalStateException.class, () -> recovery.recover(request));
        when(permissions.hasAnyPermissions(99L, "bpm:task:update")).thenReturn(true);
        engine.getRuntimeService().setVariable(request.instanceId(), PROCESS_INSTANCE_VARIABLE_START_USER_SELECT_ASSIGNEES,
                Map.of("ownerManagerReview", List.of(22L)));
        assertThrows(RuntimeException.class, () -> recovery.recover(request));
        assertFalse(engine.getRuntimeService().hasVariable(request.instanceId(), "coll_userList"));
        assertNotNull(engine.getTaskService().createTaskQuery().taskId(request.startTaskId()).singleResult());
    }

    @Test
    void postconditionFailureRollsBackVariableCompletionAndRecoveryComment() {
        doReturn(Set.of(22L)).when(invoker).calculateUsersByTask(any());
        assertThrows(RuntimeException.class, () -> recovery.recover(request));
        assertFalse(engine.getRuntimeService().hasVariable(request.instanceId(), "coll_userList"));
        assertFalse(engine.getRuntimeService().hasVariable(request.instanceId(), "bpm_start_task_recovery"));
        assertNotNull(engine.getTaskService().createTaskQuery().taskId(request.startTaskId()).singleResult());
        assertEquals(1, engine.getTaskService().getProcessInstanceComments(request.instanceId()).size());
    }

    @Test
    void businessRecheckSharesEngineTransactionAndCanAbortBeforeMutation() {
        var called = new java.util.concurrent.atomic.AtomicBoolean();
        var failure = assertThrows(RuntimeException.class, () -> recovery.recover(request, () -> {
            assertTrue(TransactionSynchronizationManager.isActualTransactionActive());
            called.set(true);
            throw new IllegalStateException("Business snapshot changed");
        }));
        assertInstanceOf(IllegalStateException.class, failure.getCause());
        assertEquals("Business snapshot changed", failure.getCause().getMessage());
        assertTrue(called.get());
        assertFalse(engine.getRuntimeService().hasVariable(request.instanceId(), "coll_userList"));
        assertNotNull(engine.getTaskService().createTaskQuery().taskId(request.startTaskId()).singleResult());
        assertEquals(1, engine.getTaskService().getProcessInstanceComments(request.instanceId()).size());
    }

    @Test
    void rejectsReentryFromTransactionCompletion() {
        TransactionSynchronizationManager.initSynchronization();
        try { assertThrows(IllegalStateException.class, () -> recovery.recover(request)); }
        finally { TransactionSynchronizationManager.clearSynchronization(); }
        assertFalse(engine.getRuntimeService().hasVariable(request.instanceId(), "coll_userList"));
    }
    @Test
    void legacyAutomaticCallbackLeavesApprovedStarterWithoutReviewer() {
        engine.getTaskService().removeVariableLocal(request.startTaskId(), TASK_VARIABLE_STATUS);
        automaticCallback(request.startTaskId());
        var task = engine.getTaskService().createTaskQuery().taskId(request.startTaskId()).singleResult();
        assertNotNull(task);
        assertEquals(2, engine.getTaskService().getVariableLocal(task.getId(), TASK_VARIABLE_STATUS));
        assertNull(engine.getHistoryService().createHistoricTaskInstanceQuery().taskId(task.getId()).singleResult().getEndTime());
        assertEquals(0, engine.getTaskService().createTaskQuery().processInstanceId(request.instanceId())
                .taskDefinitionKey("ownerManagerReview").count());
    }

    /** Exercise the actual assignment callback, Spring transaction proxy and approveTask body. */
    private void automaticCallback(String taskId) {
        var service = new BpmTaskServiceImpl();
        ReflectionTestUtils.setField(service, "taskService", engine.getTaskService());
        ReflectionTestUtils.setField(service, "runtimeService", engine.getRuntimeService());
        ReflectionTestUtils.setField(service, "historyService", engine.getHistoryService());
        var instances = mock(BpmProcessInstanceService.class);
        when(instances.getProcessInstance(anyString())).thenAnswer(call -> engine.getRuntimeService()
                .createProcessInstanceQuery().processInstanceId(call.getArgument(0)).includeProcessVariables().singleResult());
        ReflectionTestUtils.setField(service, "processInstanceService", instances);
        var definitions = mock(cn.iocoder.yudao.module.bpm.service.definition.BpmProcessDefinitionService.class);
        when(definitions.getProcessDefinitionInfo(anyString())).thenReturn(
                new cn.iocoder.yudao.module.bpm.dal.dataobject.definition.BpmProcessDefinitionInfoDO());
        ReflectionTestUtils.setField(service, "bpmProcessDefinitionService", definitions);
        var models = mock(cn.iocoder.yudao.module.bpm.service.definition.BpmModelService.class);
        when(models.getBpmnModelByDefinitionId(anyString())).thenAnswer(call -> engine.getRepositoryService().getBpmnModel(call.getArgument(0)));
        ReflectionTestUtils.setField(service, "modelService", models);
        var validators = mock(org.springframework.beans.factory.ObjectProvider.class);
        when(validators.orderedStream()).thenAnswer(call -> java.util.stream.Stream.empty());
        ReflectionTestUtils.setField(service, "taskActionValidatorProvider", validators);
        var comments = new cn.iocoder.yudao.module.bpm.service.comment.BpmCommentServiceImpl();
        ReflectionTestUtils.setField(comments, "taskService", engine.getTaskService());
        ReflectionTestUtils.setField(service, "commentService", comments);
        var factory = new org.springframework.aop.framework.ProxyFactory(service);
        factory.setProxyTargetClass(true);
        factory.addAdvice(new org.springframework.transaction.interceptor.TransactionInterceptor(transactionManager,
                new org.springframework.transaction.annotation.AnnotationTransactionAttributeSource()));
        var proxy = (BpmTaskServiceImpl) factory.getProxy();
        try (var spring = mockStatic(cn.hutool.extra.spring.SpringUtil.class)) {
            spring.when(() -> cn.hutool.extra.spring.SpringUtil.getBean(BpmTaskServiceImpl.class)).thenReturn(proxy);
            new org.springframework.transaction.support.TransactionTemplate(transactionManager).executeWithoutResult(status ->
                    service.processTaskAssigned(service.getTask(taskId)));
        }
    }

}
