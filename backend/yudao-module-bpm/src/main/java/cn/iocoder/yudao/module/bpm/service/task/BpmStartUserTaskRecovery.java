package cn.iocoder.yudao.module.bpm.service.task;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.bpm.framework.flowable.core.util.BpmnModelUtils;
import cn.iocoder.yudao.module.bpm.framework.flowable.core.util.FlowableUtils;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import org.flowable.bpmn.model.EndEvent;
import org.flowable.bpmn.model.UserTask;
import org.flowable.engine.ProcessEngine;
import org.flowable.task.api.Task;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.flowable.spring.SpringProcessEngineConfiguration;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import static cn.iocoder.yudao.module.bpm.framework.flowable.core.enums.BpmnVariableConstants.*;

/**
 * Operator-invoked recovery primitive; deliberately not a bean, startup hook or HTTP endpoint.
 * The caller must authorize the exact business record and compare its frozen reviewer snapshot
 * before invoking this helper against the existing application's configured process engine.
 */
public final class BpmStartUserTaskRecovery {
    private static final String MARKER = "bpm_start_task_recovery";
    private final ProcessEngine engine;
    private final AdminUserApi users;
    private final PermissionApi permissions;
    private final TransactionTemplate transaction;

    public BpmStartUserTaskRecovery(ProcessEngine engine, AdminUserApi users, PermissionApi permissions,
                                    PlatformTransactionManager transactionManager) {
        require(engine.getProcessEngineConfiguration() instanceof SpringProcessEngineConfiguration config
                && config.getTransactionManager() == transactionManager, "Engine transaction manager mismatch");
        this.transaction = new TransactionTemplate(transactionManager);
        this.engine = engine;
        this.users = users;
        this.permissions = permissions;
    }

    public record Request(String tenantId, String instanceId, String definitionId, String businessKey,
                          String startTaskId, String reviewTaskKey, String variableName,
                          String definitionXmlSha256, List<Long> reviewers, String reviewPermission,
                          Long operatorUserId, String reason) {
        public Request {
            reviewers = List.copyOf(reviewers);
        }
    }

    public String inspect(Request request) {
        validateOperator(request);
        return check(request);
    }

    public String recover(Request request) {
        // afterCompletion can still expose the committed transaction's resources. Recovery must
        // start at an independent operator invocation, never inside that callback or a caller TX.
        require(!TransactionSynchronizationManager.isActualTransactionActive()
                && !TransactionSynchronizationManager.isSynchronizationActive(), "Fresh invocation required");
        validateOperator(request);
        return FlowableUtils.executeAuthenticatedUserId(request.operatorUserId(), () ->
                transaction.execute(status -> {
                    String state = check(request);
                    if ("ALREADY_RECOVERED".equals(state)) return state;
                    var runtime = engine.getRuntimeService();
                    runtime.setVariable(request.instanceId(), request.variableName(), request.reviewers());
                    engine.getTaskService().addComment(request.startTaskId(), request.instanceId(),
                            "recovery", "Resume previously auto-approved starter: " + request.reason());
                    runtime.setVariable(request.instanceId(), MARKER, marker(request));
                    // Preserve the original automatic-approval status, reason and actor snapshots.
                    engine.getTaskService().complete(request.startTaskId());
                    requireReviewTasks(request);
                    return "RECOVERED";
                }));
    }

    private void validateOperator(Request r) {
        require(r != null && r.operatorUserId() != null && r.reason() != null && !r.reason().isBlank(),
                "Operator and recovery reason required");
        require(Objects.equals(String.valueOf(TenantContextHolder.getTenantId()), r.tenantId()), "Tenant mismatch");
        require(permissions.hasAnyPermissions(r.operatorUserId(), "bpm:task:update"), "Operator permission denied");
        requireEnabledUser(r.operatorUserId());
        require(r.reviewPermission() != null && !r.reviewPermission().isBlank()
                && !r.reviewers().isEmpty() && r.reviewers().stream().allMatch(id -> id != null && id > 0)
                && Set.copyOf(r.reviewers()).size() == r.reviewers().size(), "Invalid reviewer snapshot");
        for (Long reviewer : r.reviewers()) {
            requireEnabledUser(reviewer);
            require(permissions.hasAnyPermissions(reviewer, r.reviewPermission()), "Reviewer permission changed");
        }
        require(r.variableName() != null && r.variableName().matches("[A-Za-z][A-Za-z0-9_]*"), "Invalid variable name");
    }

    private void requireEnabledUser(Long id) {
        var user = users.getUser(id);
        require(user != null && Objects.equals(0, user.getStatus()), "User unavailable");
    }

    private String check(Request r) {
        var runtime = engine.getRuntimeService();
        var instance = runtime.createProcessInstanceQuery().processInstanceId(r.instanceId()).singleResult();
        require(instance != null && !instance.isSuspended()
                && Objects.equals(r.tenantId(), instance.getTenantId())
                && Objects.equals(r.definitionId(), instance.getProcessDefinitionId())
                && Objects.equals(r.businessKey(), instance.getBusinessKey()), "Instance identity/state mismatch");
        var repository = engine.getRepositoryService();
        var definition = repository.createProcessDefinitionQuery().processDefinitionId(r.definitionId()).singleResult();
        // Publishing a replacement suspends only the old definition, not its running instances.
        require(definition != null && Objects.equals(r.tenantId(), definition.getTenantId()), "Definition unavailable");
        try (var xml = repository.getProcessModel(r.definitionId())) {
            require(Objects.equals(r.definitionXmlSha256(), HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(xml.readAllBytes()))), "Definition XML changed");
        } catch (java.io.IOException | java.security.NoSuchAlgorithmException ex) {
            throw new IllegalStateException("Cannot verify definition XML", ex);
        }
        var model = repository.getBpmnModel(r.definitionId());
        var all = BpmnModelUtils.getBpmnModelElements(model, UserTask.class);
        require(all.size() == 2, "Unexpected process topology");
        var starter = all.stream().filter(t -> "StartUserNode".equals(t.getId())).findFirst().orElseThrow();
        var review = all.stream().filter(t -> Objects.equals(r.reviewTaskKey(), t.getId())).findFirst().orElseThrow();
        require(starter.getOutgoingFlows().size() == 1
                && Objects.equals(r.reviewTaskKey(), starter.getOutgoingFlows().getFirst().getTargetRef())
                && review.getOutgoingFlows().size() == 1
                && review.getOutgoingFlows().getFirst().getTargetFlowElement() instanceof EndEvent
                && Objects.equals(60, BpmnModelUtils.parseCandidateStrategy(review))
                && Objects.equals("${" + r.variableName() + "}", BpmnModelUtils.parseCandidateParam(review)),
                "Not the expected missing-expression starter failure");
        Object selected = runtime.getVariable(r.instanceId(), PROCESS_INSTANCE_VARIABLE_START_USER_SELECT_ASSIGNEES);
        require(selected instanceof Map<?, ?> map && Objects.equals(map.get(r.reviewTaskKey()), r.reviewers()),
                "Frozen assignees mismatch");
        var management = engine.getManagementService();
        require(management.createJobQuery().processInstanceId(r.instanceId()).count() == 0
                && management.createTimerJobQuery().processInstanceId(r.instanceId()).count() == 0
                && management.createSuspendedJobQuery().processInstanceId(r.instanceId()).count() == 0
                && management.createDeadLetterJobQuery().processInstanceId(r.instanceId()).count() == 0,
                "Pending engine jobs require separate recovery");
        Object previous = runtime.getVariable(r.instanceId(), MARKER);
        if (previous != null) {
            require(Objects.equals(previous, marker(r)), "Different recovery already recorded");
            requireReviewTasks(r);
            return "ALREADY_RECOVERED";
        }
        require(!runtime.hasVariable(r.instanceId(), r.variableName()), "Compatibility variable already exists");
        var tasks = engine.getTaskService().createTaskQuery().processInstanceId(r.instanceId()).list();
        require(tasks.size() == 1 && Objects.equals(r.startTaskId(), tasks.getFirst().getId())
                && "StartUserNode".equals(tasks.getFirst().getTaskDefinitionKey())
                && !tasks.getFirst().isSuspended(), "Starter task changed");
        require(r.reviewers().stream().noneMatch(id -> id.toString().equals(tasks.getFirst().getAssignee())),
                "Reviewer is starter; automatic approval needs separate review");
        require(Objects.equals(2, engine.getTaskService().getVariableLocal(r.startTaskId(), TASK_VARIABLE_STATUS)),
                "Original automatic approval status missing");
        Object actor = engine.getTaskService().getVariableLocal(r.startTaskId(), "bpm_action_actor_snapshot");
        require(actor instanceof Map<?, ?> map && "SYSTEM".equals(map.get("subjectType")),
                "Original automatic approval actor missing");
        require(engine.getHistoryService().createHistoricTaskInstanceQuery().processInstanceId(r.instanceId())
                .taskDefinitionKey(r.reviewTaskKey()).count() == 0, "Review already started");
        return "READY";
    }

    private void requireReviewTasks(Request r) {
        List<Task> tasks = engine.getTaskService().createTaskQuery().processInstanceId(r.instanceId()).list();
        require(tasks.size() == r.reviewers().size()
                && tasks.stream().allMatch(t -> Objects.equals(r.reviewTaskKey(), t.getTaskDefinitionKey()) && !t.isSuspended())
                && tasks.stream().map(Task::getAssignee).collect(Collectors.toSet())
                .equals(r.reviewers().stream().map(Object::toString).collect(Collectors.toSet())),
                "Recovery did not produce exactly the frozen reviewer tasks");
    }

    private String marker(Request r) {
        return r.definitionId() + ":" + r.startTaskId() + ":" + r.variableName() + ":" + r.reviewers();
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
