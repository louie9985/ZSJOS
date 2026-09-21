package cn.iocoder.yudao.module.bpm.api.task;

import cn.iocoder.yudao.framework.common.enums.UserTypeEnum;
import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.bpm.api.task.dto.*;
import cn.iocoder.yudao.module.bpm.controller.admin.definition.vo.model.BpmModelSaveReqVO;
import cn.iocoder.yudao.module.bpm.dal.dataobject.definition.BpmProcessDefinitionInfoDO;
import cn.iocoder.yudao.module.bpm.framework.flowable.core.behavior.BpmActivityBehaviorFactory;
import cn.iocoder.yudao.module.bpm.framework.flowable.core.candidate.BpmTaskCandidateInvoker;
import cn.iocoder.yudao.module.bpm.framework.flowable.core.candidate.strategy.dept.BpmTaskCandidateStartUserSelectStrategy;
import cn.iocoder.yudao.module.bpm.framework.flowable.core.util.*;
import cn.iocoder.yudao.module.bpm.service.definition.*;
import cn.iocoder.yudao.module.bpm.service.task.*;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import cn.iocoder.yudao.module.system.api.user.dto.AdminUserRespDTO;
import org.flowable.bpmn.model.*;
import org.flowable.engine.*;
import org.flowable.engine.delegate.DelegateExecution;
import org.flowable.engine.impl.cfg.StandaloneInMemProcessEngineConfiguration;
import org.junit.jupiter.api.*;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.*;
import java.util.*;

import static cn.iocoder.yudao.module.bpm.framework.flowable.core.enums.BpmnVariableConstants.*;
import static cn.iocoder.yudao.module.bpm.enums.ErrorCodeConstants.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Real Flowable execution with mocked System/definition storage; no production database or callbacks. */
class BpmExternalSimpleEngineTest {
    private ProcessEngine engine;
    private BpmProcessInstanceServiceImpl service;
    private BpmProcessDefinitionService definitions;
    private AdminUserApi users;
    private BpmTaskCandidateInvoker invoker;
    private final Map<String, BpmProcessDefinitionInfoDO> infos = new HashMap<>();
    private final BpmStartSubjectDTO partner = new BpmStartSubjectDTO(UserTypeEnum.PARTNER.getValue(), 30L);

    @BeforeEach
    void setup() {
        definitions = mock(BpmProcessDefinitionService.class);
        users = mock(AdminUserApi.class);
        invoker = mock(BpmTaskCandidateInvoker.class);
        var factory = new BpmActivityBehaviorFactory();
        factory.setTaskCandidateInvoker(invoker);
        factory.setProcessDefinitionService(definitions);
        var config = new StandaloneInMemProcessEngineConfiguration();
        config.setJdbcUrl("jdbc:h2:mem:external-simple-" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1");
        config.setDatabaseSchemaUpdate("create-drop");
        config.setAsyncExecutorActivate(false);
        config.setActivityBehaviorFactory(factory);
        engine = config.buildProcessEngine();
        service = new BpmProcessInstanceServiceImpl();
        ReflectionTestUtils.setField(service, "runtimeService", engine.getRuntimeService());
        ReflectionTestUtils.setField(service, "processDefinitionService", definitions);
        ReflectionTestUtils.setField(service, "adminUserApi", users);
        ReflectionTestUtils.setField(service, "processInstanceRelationService", mock(BpmProcessInstanceRelationService.class));
        var models = mock(BpmModelService.class);
        ReflectionTestUtils.setField(service, "modelService", models);
        when(models.getBpmnModelByDefinitionId(anyString())).thenAnswer(a ->
                engine.getRepositoryService().getBpmnModel(a.getArgument(0)));
        when(definitions.getProcessDefinitionInfo(anyString())).thenAnswer(a -> infos.get(a.getArgument(0)));
        when(definitions.getActiveProcessDefinition(anyString())).thenAnswer(a -> engine.getRepositoryService()
                .createProcessDefinitionQuery().processDefinitionKey(a.getArgument(0)).latestVersion().singleResult());
        var provider = mock(BpmExternalStartUserProvider.class);
        when(provider.getUserType()).thenReturn(partner.getUserType());
        when(provider.validateAndGetDisplayName(30L)).thenReturn("External test subject");
        ReflectionTestUtils.setField(service, "externalStartUserProviders", List.of(provider));
        when(users.getUserMap(anyCollection())).thenReturn(Map.of(30L,
                new AdminUserRespDTO().setId(30L).setStatus(0), 31L,
                new AdminUserRespDTO().setId(31L).setStatus(0)));
        var lookup = mock(BpmProcessInstanceService.class);
        when(lookup.getProcessInstance(anyString())).thenAnswer(a -> engine.getRuntimeService()
                .createProcessInstanceQuery().processInstanceId(a.getArgument(0)).includeProcessVariables().singleResult());
        var strategy = new BpmTaskCandidateStartUserSelectStrategy();
        ReflectionTestUtils.setField(strategy, "processInstanceService", lookup);
        when(invoker.calculateUsersByTask(any())).thenAnswer(a -> {
            DelegateExecution execution = a.getArgument(0);
            assertNotEquals("StartUserNode", execution.getCurrentActivityId(), "External submission must not resolve ADMIN candidates");
            return strategy.calculateUsersByTask(execution, null);
        });
    }

    @AfterEach
    void close() {
        if (engine != null) engine.close();
    }

    @Test
    void externalSubmissionAssignsRealStrategyCandidatesAndOldInstancesCoexist() throws Exception {
        for (String key : List.of("zsjos_partner_withdrawal", "zsjos_lead_appeal_review")) {
            String taskKey = key.contains("withdrawal") ? "financeReview" : "appealReview";
            var old = BpmnModelUtils.getBpmnModel(Files.readAllBytes(asset(key, "1.0.0/process.bpmn20.xml")));
            deploy(key, old, new BpmProcessDefinitionInfoDO().setModelType(10));
            String oldId = start(key, taskKey);
            String oldDefinition = engine.getRuntimeService().createProcessInstanceQuery().processInstanceId(oldId)
                    .singleResult().getProcessDefinitionId();
            var req = read(key);
            deploy(key, SimpleModelUtils.buildBpmnModel(key, req.getName(), req.getSimpleModel()), info(req));
            String id = start(key, taskKey);
            var instance = engine.getRuntimeService().createProcessInstanceQuery().processInstanceId(id).singleResult();
            assertEquals(partner.toFlowableId(), instance.getStartUserId());
            assertNotEquals(oldDefinition, instance.getProcessDefinitionId());
            var tasks = engine.getTaskService().createTaskQuery().processInstanceId(id).list();
            assertEquals(Set.of("30", "31"), new HashSet<>(tasks.stream().map(t -> t.getAssignee()).toList()));
            assertTrue(tasks.stream().allMatch(t -> taskKey.equals(t.getTaskDefinitionKey())));
            assertEquals(0, engine.getHistoryService().createHistoricTaskInstanceQuery().processInstanceId(id)
                    .taskDefinitionKey("StartUserNode").count());
            assertEquals(1, engine.getHistoryService().createHistoricActivityInstanceQuery().processInstanceId(id)
                    .activityId("StartUserNode").finished().count());
            engine.getTaskService().complete(tasks.getFirst().getId());
            assertNull(engine.getRuntimeService().createProcessInstanceQuery().processInstanceId(id).singleResult());
            assertEquals(2, engine.getTaskService().createTaskQuery().processInstanceId(oldId).count());
            engine.getRuntimeService().deleteProcessInstance(oldId, "test cancellation");
            assertNotNull(engine.getHistoryService().createHistoricProcessInstanceQuery().processInstanceId(oldId)
                    .finished().singleResult());
        }
        verify(users, never()).getUser(anyLong());
    }

    @Test
    void rejectsMissingDisabledAndUnknownTaskCandidatesBeforeEngineStart() throws Exception {
        var req = read("zsjos_partner_withdrawal");
        deploy(req.getKey(), SimpleModelUtils.buildBpmnModel(req.getKey(), req.getName(), req.getSimpleModel()), info(req));
        var request = request(req.getKey(), "financeReview");
        request.setStartUserSelectAssignees(Map.of());
        assertEquals(PROCESS_INSTANCE_START_USER_SELECT_ASSIGNEES_NOT_CONFIG.getCode(),
                startFailure(request).getCode());
        request.setStartUserSelectAssignees(Map.of("financeReview", List.of(30L), "unknown", List.of(31L)));
        assertEquals(PROCESS_INSTANCE_EXTERNAL_CANDIDATE_UNSUPPORTED.getCode(),
                startFailure(request).getCode());
        request.setStartUserSelectAssignees(Map.of("financeReview", List.of(30L)));
        when(users.getUserMap(anyCollection())).thenReturn(Map.of(30L, new AdminUserRespDTO().setId(30L).setStatus(1)));
        assertEquals(PROCESS_INSTANCE_START_USER_SELECT_ASSIGNEES_NOT_EXISTS.getCode(),
                startFailure(request).getCode());
        assertEquals(0, engine.getRuntimeService().createProcessInstanceQuery().count());
    }

    @Test
    void rejectsMislabelledOrNonRootStarterApprovals() throws Exception {
        var req = read("zsjos_partner_withdrawal");
        var model = SimpleModelUtils.buildBpmnModel(req.getKey(), req.getName(), req.getSimpleModel());
        var task = (UserTask) model.getMainProcess().getFlowElement("StartUserNode");
        var snapshot = info(req);
        assertTrue(BpmExternalStartUtils.isSubmissionTask(snapshot, model, task));
        snapshot.setModelType(10);
        assertFalse(BpmExternalStartUtils.isSubmissionTask(snapshot, model, task));
        snapshot.setModelType(20);
        req.getSimpleModel().setType(11);
        snapshot.setSimpleModel(JsonUtils.toJsonString(req.getSimpleModel()));
        assertFalse(BpmExternalStartUtils.isSubmissionTask(snapshot, model, task));
        req.getSimpleModel().setType(10);
        snapshot.setSimpleModel(JsonUtils.toJsonString(req.getSimpleModel()));
        model.getMainProcess().addFlowElement(new SequenceFlow("financeReview", "StartUserNode"));
        assertFalse(BpmExternalStartUtils.isSubmissionTask(snapshot, model, task));
    }

    @Test
    void orderDefinitionsCoexistAndRealTaskServiceUsesEachInstancesReasonPolicy() throws Exception {
        String key = "zsjos_sales_order_dual_approval";
        doReturn(Set.of(30L)).when(invoker).calculateUsersByTask(any());
        var taskService = new BpmTaskServiceImpl();
        ReflectionTestUtils.setField(taskService, "taskService", engine.getTaskService());
        ReflectionTestUtils.setField(taskService, "runtimeService", engine.getRuntimeService());
        ReflectionTestUtils.setField(taskService, "bpmProcessDefinitionService", definitions);
        var lookup = mock(BpmProcessInstanceService.class);
        when(lookup.getProcessInstance(anyString())).thenAnswer(a -> engine.getRuntimeService()
                .createProcessInstanceQuery().processInstanceId(a.getArgument(0)).includeProcessVariables().singleResult());
        ReflectionTestUtils.setField(taskService, "processInstanceService", lookup);
        var models = mock(BpmModelService.class);
        when(models.getBpmnModelByDefinitionId(anyString())).thenAnswer(a -> engine.getRepositoryService().getBpmnModel(a.getArgument(0)));
        ReflectionTestUtils.setField(taskService, "modelService", models);
        ReflectionTestUtils.setField(taskService, "commentService", mock(cn.iocoder.yudao.module.bpm.service.comment.BpmCommentService.class));
        var validators = mock(org.springframework.beans.factory.ObjectProvider.class);
        when(validators.orderedStream()).thenAnswer(a -> java.util.stream.Stream.empty());
        ReflectionTestUtils.setField(taskService, "taskActionValidatorProvider", validators);
        var old = read(key);
        deploy(key, SimpleModelUtils.buildBpmnModel(key, old.getName(), old.getSimpleModel()), info(old));
        String oldId = startOrderForReasonTest(key);
        var next = JsonUtils.parseObject(Files.readString(asset(key, "2.1.0/process-model.json")), BpmModelSaveReqVO.class);
        deploy(key, SimpleModelUtils.buildBpmnModel(key, next.getName(), next.getSimpleModel()), info(next));
        String newId = startOrderForReasonTest(key);
        var oldTasks = engine.getTaskService().createTaskQuery().processInstanceId(oldId).list();
        assertEquals(2, oldTasks.size());
        for (var task : oldTasks) {
            var error = assertThrows(ServiceException.class, () -> taskService.approveTask(30L,
                    new cn.iocoder.yudao.module.bpm.controller.admin.task.vo.task.BpmTaskApproveReqVO().setId(task.getId()).setReason("")));
            assertEquals(TASK_REASON_REQUIRE.getCode(), error.getCode());
        }
        var newTasks = engine.getTaskService().createTaskQuery().processInstanceId(newId).list();
        assertEquals(2, newTasks.size());
        for (var task : newTasks) {
            taskService.approveTask(30L, new cn.iocoder.yudao.module.bpm.controller.admin.task.vo.task.BpmTaskApproveReqVO()
                    .setId(task.getId()).setReason(""));
        }
        assertNull(engine.getRuntimeService().createProcessInstanceQuery().processInstanceId(newId).singleResult());
        assertEquals(2, engine.getTaskService().createTaskQuery().processInstanceId(oldId).count());
    }

    private String startOrderForReasonTest(String key) {
        var instance = FlowableUtils.executeAuthenticatedUserId(30L, () -> engine.getRuntimeService()
                .startProcessInstanceByKey(key, Map.of(PROCESS_INSTANCE_VARIABLE_START_USER_ID, "30")));
        var starter = engine.getTaskService().createTaskQuery().processInstanceId(instance.getId()).singleResult();
        engine.getTaskService().complete(starter.getId());
        return instance.getId();
    }

    private String start(String key, String taskKey) {
        return service.createProcessInstance(partner, request(key, taskKey));
    }

    @Test
    void adminIdentityIsNotReplacedBySpoofedExternalVariable() throws Exception {
        var req = read("zsjos_partner_withdrawal");
        deploy(req.getKey(), SimpleModelUtils.buildBpmnModel(req.getKey(), req.getName(), req.getSimpleModel()), info(req));
        doReturn(Set.of(30L)).when(invoker).calculateUsersByTask(any());
        var instance = FlowableUtils.executeAuthenticatedUserId(30L, () -> engine.getRuntimeService()
                .startProcessInstanceByKey(req.getKey(), Map.of(PROCESS_INSTANCE_VARIABLE_START_USER_ID, partner.toFlowableId())));
        var submission = engine.getTaskService().createTaskQuery().processInstanceId(instance.getId()).singleResult();
        assertEquals("StartUserNode", submission.getTaskDefinitionKey());
        assertEquals("30", submission.getAssignee());
        verify(invoker).calculateUsersByTask(any());
    }

    @Test
    void businessStarterStrategiesRemainForbiddenInSimpleDefinitions() throws Exception {
        for (int strategy : List.of(36, 37, 38)) {
            var req = read("zsjos_partner_withdrawal");
            req.getSimpleModel().getChildNode().setCandidateStrategy(strategy);
            deploy(req.getKey(), SimpleModelUtils.buildBpmnModel(req.getKey(), req.getName(), req.getSimpleModel()), info(req));
            assertEquals(PROCESS_INSTANCE_EXTERNAL_CANDIDATE_UNSUPPORTED.getCode(),
                    startFailure(request(req.getKey(), "financeReview")).getCode());
        }
        assertEquals(0, engine.getRuntimeService().createProcessInstanceQuery().count());
    }

    private ServiceException startFailure(BpmProcessInstanceCreateReqDTO request) {
        // The existing authenticated-user wrapper preserves business exceptions as its cause.
        var failure = assertThrows(RuntimeException.class, () -> service.createProcessInstance(partner, request));
        return assertInstanceOf(ServiceException.class, failure.getCause());
    }

    @Test
    void externalManualSubmissionIsRejectedInsteadOfSilentlyApproved() throws Exception {
        var req = read("zsjos_partner_withdrawal");
        deploy(req.getKey(), SimpleModelUtils.buildBpmnModel(req.getKey(), req.getName(), req.getSimpleModel()), info(req));
        for (var flag : Map.of(PROCESS_INSTANCE_VARIABLE_SKIP_START_USER_NODE, false,
                String.format(PROCESS_INSTANCE_VARIABLE_RETURN_FLAG, "StartUserNode"), true).entrySet()) {
            var request = request(req.getKey(), "financeReview");
            request.getVariables().put(flag.getKey(), flag.getValue());
            Throwable failure = assertThrows(RuntimeException.class, () -> service.createProcessInstance(partner, request));
            while (failure.getCause() != null) failure = failure.getCause();
            assertEquals(PROCESS_INSTANCE_EXTERNAL_CANDIDATE_UNSUPPORTED.getCode(),
                    assertInstanceOf(ServiceException.class, failure).getCode());
            assertEquals(0, engine.getTaskService().createTaskQuery().count());
            assertEquals(0, engine.getRuntimeService().createProcessInstanceQuery().count());
        }
    }

    private BpmProcessInstanceCreateReqDTO request(String key, String taskKey) {
        return new BpmProcessInstanceCreateReqDTO().setProcessDefinitionKey(key).setBusinessKey("test:" + UUID.randomUUID())
                .setStartUserSelectAssignees(Map.of(taskKey, List.of(30L, 31L)))
                .setVariables(new HashMap<>(Map.of(PROCESS_INSTANCE_VARIABLE_START_USER_ID, "30",
                        PROCESS_INSTANCE_VARIABLE_START_USER_SELECT_ASSIGNEES, Map.of(taskKey, List.of(999L)))));
    }

    private void deploy(String key, BpmnModel model, BpmProcessDefinitionInfoDO info) {
        var deployment = engine.getRepositoryService().createDeployment().addBpmnModel(key + ".bpmn20.xml", model).deploy();
        var definition = engine.getRepositoryService().createProcessDefinitionQuery().deploymentId(deployment.getId()).singleResult();
        infos.put(definition.getId(), info);
    }

    private BpmProcessDefinitionInfoDO info(BpmModelSaveReqVO req) {
        return new BpmProcessDefinitionInfoDO().setModelType(20).setSimpleModel(JsonUtils.toJsonString(req.getSimpleModel()));
    }

    private BpmModelSaveReqVO read(String key) throws Exception {
        return JsonUtils.parseObject(Files.readString(asset(key, "2.0.0/process-model.json")), BpmModelSaveReqVO.class);
    }

    private Path asset(String key, String suffix) {
        Path root = Path.of("").toAbsolutePath();
        while (root != null && !Files.exists(root.resolve("script/bpm/manifest.json"))) root = root.getParent();
        return Objects.requireNonNull(root).resolve("script/bpm/" + key + "/" + suffix);
    }
}
