package cn.iocoder.yudao.module.bpm.api.task;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.common.exception.ErrorCode;
import cn.iocoder.yudao.module.bpm.api.task.dto.*;
import cn.iocoder.yudao.module.bpm.service.task.BpmTaskService;
import cn.iocoder.yudao.module.bpm.service.task.BpmProcessInstanceService;
import cn.iocoder.yudao.module.bpm.service.definition.BpmProcessDefinitionService;
import cn.iocoder.yudao.module.bpm.framework.flowable.core.util.*;
import cn.iocoder.yudao.module.bpm.framework.flowable.core.enums.BpmnVariableConstants;
import cn.iocoder.yudao.module.bpm.enums.task.BpmProcessInstanceStatusEnum;
import jakarta.annotation.Resource;
import org.flowable.bpmn.model.*;
import org.flowable.engine.TaskService;
import org.flowable.engine.runtime.ProcessInstance;
import org.flowable.task.api.Task;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.ZoneId;
import java.util.*;
import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;

@Service
public class BpmBusinessReviewApiImpl implements BpmBusinessReviewApi {
    public static final ErrorCode INVALID_REVIEW = new ErrorCode(1_009_007_090, "业务审批任务不可办理：{}");
    @Resource private BpmTaskService taskService;
    @Resource private TaskService engineTasks;
    @Resource private BpmProcessInstanceService instanceService;
    @Resource private BpmProcessDefinitionService definitionService;
    @Resource private BpmProcessTaskApi taskApi;
    @Resource private ObjectProvider<BpmBusinessReviewPolicy> policies;

    @Override
    public BpmBusinessReviewState inspect(String instanceId, String definitionKey, String businessKey, String nodeKey) {
        BpmBusinessReviewState result = new BpmBusinessReviewState();
        if (StrUtil.isBlank(instanceId)) return result.setProblem("缺少审批流程，请联系管理员核对");
        var history = instanceService.getHistoricProcessInstance(instanceId);
        if (history == null || !Objects.equals(history.getTenantId(), FlowableUtils.getTenantId())
                || !Objects.equals(history.getProcessDefinitionKey(), definitionKey)
                || !Objects.equals(history.getBusinessKey(), businessKey)) {
            return result.setProblem("流程不存在或与当前业务、租户不匹配");
        }
        var variables = history.getProcessVariables();
        result.setProcessStatus(variables == null ? null : (Integer) variables.get(BpmnVariableConstants.PROCESS_INSTANCE_VARIABLE_STATUS));
        if (history.getEndTime() != null) result.setEndedAt(history.getEndTime().toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime());
        // Cancelled sibling tasks are not review evidence: only an actual approve/reject decision counts.
        var decisions = taskService.getTaskListByProcessInstanceIds(Set.of(instanceId)).stream()
                .filter(t -> Objects.equals(nodeKey, t.getTaskDefinitionKey()) && t.getParentTaskId() == null
                        && t.getEndTime() != null && t.getTaskLocalVariables() != null)
                .filter(t -> Set.of(2, 3).contains(t.getTaskLocalVariables().getOrDefault(BpmnVariableConstants.TASK_VARIABLE_STATUS, 0)))
                .sorted(Comparator.comparing(org.flowable.task.api.history.HistoricTaskInstance::getEndTime).reversed()).toList();
        if (!decisions.isEmpty()) {
            var task = decisions.getFirst();
            Long actor = BpmTaskActorSnapshot.userId(task.getTaskLocalVariables());
            result.setReviewerUserId(actor != null ? actor : numericId(task.getAssignee()));
            result.setReviewerName(BpmTaskActorSnapshot.name(task.getTaskLocalVariables()));
            result.setReason(task.getDescription());
            result.setReviewedAt(task.getEndTime().toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime());
        }
        if (history.getEndTime() != null) return result.setProblem("流程已结束，请刷新；业务未同步时联系管理员核对");
        ProcessInstance instance = instanceService.getProcessInstance(instanceId);
        if (instance == null || instance.isSuspended()) return result.setProblem("流程不存在或已挂起");
        List<Task> tasks = taskService.getTasksByProcessInstanceIds(List.of(instanceId));
        String problem = taskProblem(tasks, nodeKey, definitionService.getProcessDefinitionBpmnModel(history.getProcessDefinitionId()));
        result.setTaskIds(tasks.stream().map(Task::getId).sorted().toList());
        result.setAssigneeUserIds(tasks.stream().map(t -> numericId(t.getAssignee())).filter(Objects::nonNull).distinct().sorted().toList());
        return result.setActionable(problem == null).setProblem(problem);
    }

    static String taskProblem(List<Task> tasks, String nodeKey, BpmnModel model) {
        if (tasks.isEmpty()) return "没有可处理的财务审批任务";
        if (tasks.stream().anyMatch(t -> !Objects.equals(t.getTenantId(), FlowableUtils.getTenantId())
                || !Objects.equals(nodeKey, t.getTaskDefinitionKey()) || StrUtil.isNotBlank(t.getParentTaskId())
                || StrUtil.isNotBlank(t.getScopeType()) || t.getDelegationState() != null || t.isSuspended())) {
            return "任务存在非预期节点、加签、委派或挂起，请联系管理员核对";
        }
        FlowElement element = model == null ? null : model.getFlowElement(nodeKey);
        if (!(element instanceof UserTask userTask)) return "财务审批节点不存在";
        var loop = userTask.getLoopCharacteristics();
        String completion = loop == null ? "" : StrUtil.removeAll(loop.getCompletionCondition(), ' ', '\n', '\r', '\t');
        if (loop == null || loop.isSequential() || !"${nrOfCompletedInstances>0}".equals(completion)
                || !"3".equals(BpmnModelUtils.parseExtensionElement(userTask, "approveMethod"))
                || Boolean.TRUE.equals(BpmnModelUtils.parseSignEnable(model, nodeKey))
                || StrUtil.isNotBlank(BpmnModelUtils.parseReturnTaskId(userTask))
                || userTask.getOutgoingFlows().size() != 1
                || !(model.getFlowElement(userTask.getOutgoingFlows().getFirst().getTargetRef()) instanceof EndEvent)) {
            return "流程不是受支持的一人完成财务审核结构，请联系管理员核对";
        }
        return null;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void decide(Long userId, String instanceId, String definitionKey, String businessKey,
                       String nodeKey, boolean approve, String reason) {
        var matches = policies.orderedStream().filter(p -> p.supports(definitionKey, nodeKey)).toList();
        if (matches.size() != 1) throw exception(INVALID_REVIEW, "未配置唯一的业务授权策略");
        var context = new BpmTaskActionContext().setUserId(userId).setProcessInstanceId(instanceId)
                .setProcessDefinitionKey(definitionKey).setBusinessKey(businessKey).setTaskDefinitionKey(nodeKey)
                .setTenantId(FlowableUtils.getTenantId()).setReason(reason)
                .setAction(approve ? BpmTaskActionValidator.ACTION_APPROVE : BpmTaskActionValidator.ACTION_REJECT);
        matches.getFirst().validate(context);
        BpmBusinessReviewState state = inspect(instanceId, definitionKey, businessKey, nodeKey);
        if (!state.isActionable()) throw exception(INVALID_REVIEW, state.getProblem());
        Task selected = taskService.getTasksByProcessInstanceIds(List.of(instanceId)).stream()
                .sorted(Comparator.<Task, Boolean>comparing(t -> !String.valueOf(userId).equals(t.getAssignee()))
                        .thenComparing(Task::getId)).findFirst().orElseThrow(() -> exception(INVALID_REVIEW, "任务已结束，请刷新"));
        context.setTaskId(selected.getId());
        matches.getFirst().validate(context);
        if (!String.valueOf(userId).equals(selected.getAssignee())) {
            engineTasks.addComment(selected.getId(), instanceId, "business-takeover",
                    "Business review takeover; previous assignee=" + Objects.toString(selected.getAssignee(), "") + "; actor=" + userId);
            engineTasks.setAssignee(selected.getId(), String.valueOf(userId));
        }
        var decision = new BpmTaskDecisionReqDTO().setTaskId(selected.getId()).setReason(reason);
        if (approve) taskApi.approveTask(userId, decision); else taskApi.rejectTask(userId, decision);
        if (instanceService.getProcessInstance(instanceId) != null) throw exception(INVALID_REVIEW, "审批未结束，已回滚，请联系管理员核对");
    }

    private static Long numericId(String value) {
        try { return value == null ? null : Long.valueOf(value); } catch (NumberFormatException ignored) { return null; }
    }
}
