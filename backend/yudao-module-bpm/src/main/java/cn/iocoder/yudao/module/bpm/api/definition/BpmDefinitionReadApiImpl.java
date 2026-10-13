package cn.iocoder.yudao.module.bpm.api.definition;

import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.module.bpm.api.definition.dto.BpmFormMetadataRespDTO;
import cn.iocoder.yudao.module.bpm.api.definition.dto.BpmProcessDefinitionMetadataRespDTO;
import cn.iocoder.yudao.module.bpm.api.definition.dto.BpmUserTaskMetadataRespDTO;
import cn.iocoder.yudao.module.bpm.dal.dataobject.definition.BpmFormDO;
import cn.iocoder.yudao.module.bpm.dal.dataobject.definition.BpmProcessDefinitionInfoDO;
import cn.iocoder.yudao.module.bpm.service.definition.BpmFormService;
import cn.iocoder.yudao.module.bpm.service.definition.BpmProcessDefinitionService;
import cn.iocoder.yudao.module.bpm.framework.flowable.core.util.BpmnModelUtils;
import jakarta.annotation.Resource;
import org.flowable.engine.repository.ProcessDefinition;
import org.flowable.bpmn.model.BpmnModel;
import org.flowable.bpmn.model.BoundaryEvent;
import org.flowable.bpmn.model.EndEvent;
import org.flowable.bpmn.model.FlowElement;
import org.flowable.bpmn.model.SequenceFlow;
import org.flowable.bpmn.model.StartEvent;
import org.flowable.bpmn.model.UserTask;
import org.flowable.bpmn.model.TimerEventDefinition;
import cn.iocoder.yudao.module.bpm.enums.definition.BpmBoundaryEventTypeEnum;
import cn.iocoder.yudao.module.bpm.enums.definition.BpmAutoApproveTypeEnum;
import cn.iocoder.yudao.module.bpm.enums.definition.BpmUserTaskTimeoutHandlerTypeEnum;
import cn.iocoder.yudao.module.bpm.framework.flowable.core.enums.BpmnModelConstants;
import org.flowable.common.engine.impl.db.SuspensionState;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
public class BpmDefinitionReadApiImpl implements BpmDefinitionReadApi {

    @Resource private cn.iocoder.yudao.module.bpm.service.task.BpmStarterSelectionPolicy starterSelectionPolicy;

    @Override
    public cn.iocoder.yudao.module.bpm.api.definition.dto.BpmStarterSelectionScopeRespDTO getStarterSelectionScope(
            String definitionId, String taskKey) {
        var model = processDefinitionService.getProcessDefinitionBpmnModel(definitionId);
        var task = model == null ? null : model.getMainProcess().getFlowElement(taskKey, true);
        if (!(task instanceof org.flowable.bpmn.model.UserTask userTask)
                || !java.util.Objects.equals(BpmnModelUtils.parseCandidateStrategy(userTask),
                cn.iocoder.yudao.module.bpm.framework.flowable.core.enums.BpmTaskCandidateStrategyEnum.START_USER_SELECT.getStrategy()))
            throw cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception(
                    cn.iocoder.yudao.module.bpm.service.task.BpmStarterSelectionPolicy.INVALID_SCOPE);
        return starterSelectionPolicy.resolve(BpmnModelUtils.parseCandidateParam(userTask));
    }

    @Resource
    private BpmFormService formService;
    @Resource
    private BpmProcessDefinitionService processDefinitionService;

    @Override
    public BpmFormMetadataRespDTO getForm(Long id) {
        BpmFormDO form = id == null ? null : formService.getForm(id);
        return form == null ? null : BeanUtils.toBean(form, BpmFormMetadataRespDTO.class);
    }

    @Override
    public List<BpmFormMetadataRespDTO> getForms() {
        return BeanUtils.toBean(formService.getFormList(), BpmFormMetadataRespDTO.class);
    }

    @Override
    public BpmProcessDefinitionMetadataRespDTO getPublishedProcessDefinition(String key) {
        ProcessDefinition definition = key == null ? null : processDefinitionService.getActiveProcessDefinition(key);
        return definition == null ? null : toMetadata(definition);
    }

    @Override
    public List<BpmProcessDefinitionMetadataRespDTO> getPublishedProcessDefinitions() {
        List<ProcessDefinition> definitions = processDefinitionService.getProcessDefinitionListBySuspensionState(
                SuspensionState.ACTIVE.getStateCode());
        Map<String, ProcessDefinition> latestByKey = new LinkedHashMap<>();
        definitions.stream()
                .sorted(Comparator.comparing(ProcessDefinition::getVersion).reversed())
                .forEach(definition -> latestByKey.putIfAbsent(definition.getKey(), definition));
        return latestByKey.values().stream().map(this::toMetadata).toList();
    }

    @Override
    public List<BpmProcessDefinitionMetadataRespDTO> getPublishedProcessDefinitions(String category) {
        return getPublishedProcessDefinitions().stream()
                .filter(definition -> Objects.equals(category, definition.getCategory()))
                .toList();
    }

    private BpmProcessDefinitionMetadataRespDTO toMetadata(ProcessDefinition definition) {
        BpmProcessDefinitionInfoDO info = processDefinitionService.getProcessDefinitionInfo(definition.getId());
        BpmnModel model = processDefinitionService.getProcessDefinitionBpmnModel(definition.getId());
        List<UserTask> userTasks = BpmnModelUtils.getBpmnModelElements(model, UserTask.class);
        BpmProcessDefinitionMetadataRespDTO result = new BpmProcessDefinitionMetadataRespDTO();
        result.setId(definition.getId());
        result.setKey(definition.getKey());
        result.setName(definition.getName());
        result.setVersion(definition.getVersion());
        result.setDeploymentId(definition.getDeploymentId());
        result.setSuspended(definition.isSuspended());
        result.setCategory(info == null ? null : info.getCategory());
        result.setFormId(info == null ? null : info.getFormId());
        result.setDescription(info == null ? null : info.getDescription());
        result.setSimpleSequentialApproval(isSimpleSequentialApproval(model, userTasks));
        List<String> chain = strictChain(model, userTasks);
        result.setOrderedUserTaskKeys(chain);
        result.setMandatoryHumanChain(!chain.isEmpty() && info != null
                && Objects.equals(info.getAutoApprovalType(), 0));
        result.setConsecutiveApprovalDeduplicationChain(!chain.isEmpty() && info != null
                && Objects.equals(info.getAutoApprovalType(), BpmAutoApproveTypeEnum.APPROVE_SEQUENT.getType()));
        result.setUserTasks(userTasks.stream().map(task -> {
                    BpmUserTaskMetadataRespDTO userTask = new BpmUserTaskMetadataRespDTO();
                    userTask.setCandidateStrategy(BpmnModelUtils.parseCandidateStrategy(task));
                    userTask.setCandidateParam(BpmnModelUtils.parseCandidateParam(task));
                    userTask.setMandatoryHumanApproval(isMandatoryHumanTask(task));
                    userTask.setKey(task.getId());
                    userTask.setName(task.getName());
                    userTask.setStartUserSelectAssignees(Objects.equals(
                            BpmnModelUtils.parseCandidateStrategy(task),
                            cn.iocoder.yudao.module.bpm.framework.flowable.core.enums.BpmTaskCandidateStrategyEnum
                                    .START_USER_SELECT.getStrategy()));
                    userTask.setRejectEndsProcess(BpmnModelUtils.parseRejectHandlerType(task)
                            != cn.iocoder.yudao.module.bpm.enums.definition.BpmUserTaskRejectHandlerTypeEnum.RETURN_USER_TASK);
                    userTask.setExecutionMode(task.getLoopCharacteristics() == null ? "SINGLE"
                            : task.getLoopCharacteristics().isSequential()
                            ? "SEQUENTIAL_MULTI_INSTANCE" : "PARALLEL_MULTI_INSTANCE");
                    userTask.setNextUserTaskKeys(BpmnModelUtils.getNextUserTasks(task).stream()
                            .map(UserTask::getId).distinct().toList());
                    return userTask;
                }).toList());
        return result;
    }

    private boolean isSimpleSequentialApproval(BpmnModel model, List<UserTask> userTasks) {
        if (model == null || model.getMainProcess() == null || userTasks.size() != 2
                || userTasks.stream().anyMatch(task -> task.getLoopCharacteristics() != null)) {
            return false;
        }
        Collection<FlowElement> elements = model.getMainProcess().getFlowElements();
        if (elements.stream().anyMatch(element -> !(element instanceof StartEvent)
                && !(element instanceof UserTask) && !(element instanceof EndEvent)
                && !(element instanceof SequenceFlow))) {
            return false;
        }
        List<StartEvent> starts = elements.stream().filter(StartEvent.class::isInstance)
                .map(StartEvent.class::cast).toList();
        List<EndEvent> ends = elements.stream().filter(EndEvent.class::isInstance)
                .map(EndEvent.class::cast).toList();
        List<SequenceFlow> flows = elements.stream().filter(SequenceFlow.class::isInstance)
                .map(SequenceFlow.class::cast).toList();
        if (starts.size() != 1 || ends.size() != 1 || flows.size() != 3) return false;

        Map<String, List<String>> outgoing = new LinkedHashMap<>();
        for (SequenceFlow flow : flows) {
            outgoing.computeIfAbsent(flow.getSourceRef(), ignored -> new ArrayList<>()).add(flow.getTargetRef());
        }
        String firstTask = onlyTarget(outgoing, starts.getFirst().getId());
        String secondTask = onlyTarget(outgoing, firstTask);
        String end = onlyTarget(outgoing, secondTask);
        return firstTask != null && secondTask != null && end != null
                && !Objects.equals(firstTask, secondTask)
                && userTasks.stream().map(UserTask::getId).collect(java.util.stream.Collectors.toSet())
                .equals(java.util.Set.of(firstTask, secondTask))
                && Objects.equals(end, ends.getFirst().getId())
                && !outgoing.containsKey(end);
    }

    private List<String> strictChain(BpmnModel model, List<UserTask> tasks) {
        if (model == null || model.getMainProcess() == null) return List.of();
        var elements = model.getMainProcess().getFlowElements();
        if (elements.stream().anyMatch(e -> !(e instanceof StartEvent) && !(e instanceof EndEvent)
                && !(e instanceof UserTask) && !(e instanceof SequenceFlow)
                && !(e instanceof BoundaryEvent event && isPureReminder(event, tasks, elements)))) return List.of();
        var starts = elements.stream().filter(StartEvent.class::isInstance).map(StartEvent.class::cast).toList();
        var ends = elements.stream().filter(EndEvent.class::isInstance).map(EndEvent.class::cast).toList();
        if (starts.size() != 1 || ends.size() != 1) return List.of();
        Map<String, List<String>> outgoing = new LinkedHashMap<>();
        for (var element : elements) if (element instanceof SequenceFlow flow) {
            if (flow.getConditionExpression() != null && !flow.getConditionExpression().isBlank()) return List.of();
            outgoing.computeIfAbsent(flow.getSourceRef(), k -> new ArrayList<>()).add(flow.getTargetRef());
        }
        List<String> result = new ArrayList<>();
        String cursor = onlyTarget(outgoing, starts.getFirst().getId());
        while (cursor != null && !Objects.equals(cursor, ends.getFirst().getId())) {
            if (result.contains(cursor)) return List.of();
            String current = cursor;
            if (tasks.stream().noneMatch(t -> t.getId().equals(current))) return List.of();
            result.add(cursor); cursor = onlyTarget(outgoing, cursor);
        }
        return cursor != null && result.size() == tasks.size() && outgoing.size() == tasks.size() + 1
                ? result : List.of();
    }

    private boolean isMandatoryHumanTask(UserTask task) {
        return Objects.equals(BpmnModelUtils.parseApproveType(task), 1)
                && Objects.equals(BpmnModelUtils.parseAssignEmptyHandlerType(task), 2)
                && Objects.equals(BpmnModelUtils.parseAssignStartUserHandlerType(task), 1)
                && (task.getSkipExpression() == null || task.getSkipExpression().isBlank());
    }

    private boolean isPureReminder(BoundaryEvent event, List<UserTask> tasks, Collection<FlowElement> elements) {
        // BPM reminder timers only notify: they must neither interrupt nor introduce another execution path.
        return !event.isCancelActivity()
                && event.getEventDefinitions().size() == 1
                && event.getEventDefinitions().getFirst() instanceof TimerEventDefinition
                && event.getExecutionListeners().isEmpty()
                && event.getIncomingFlows().isEmpty() && event.getOutgoingFlows().isEmpty()
                && elements.stream().filter(SequenceFlow.class::isInstance).map(SequenceFlow.class::cast)
                .noneMatch(flow -> Objects.equals(event.getId(), flow.getSourceRef())
                        || Objects.equals(event.getId(), flow.getTargetRef()))
                && tasks.stream().anyMatch(task -> Objects.equals(task.getId(), event.getAttachedToRefId())
                        && isMandatoryHumanTask(task))
                && Objects.equals(BpmnModelUtils.parseBoundaryEventExtensionElement(event,
                        BpmnModelConstants.BOUNDARY_EVENT_TYPE),
                        String.valueOf(BpmBoundaryEventTypeEnum.USER_TASK_TIMEOUT.getType()))
                && Objects.equals(BpmnModelUtils.parseBoundaryEventExtensionElement(event,
                        BpmnModelConstants.USER_TASK_TIMEOUT_HANDLER_TYPE),
                        String.valueOf(BpmUserTaskTimeoutHandlerTypeEnum.REMINDER.getType()));
    }

    private String onlyTarget(Map<String, List<String>> outgoing, String source) {
        List<String> targets = source == null ? null : outgoing.get(source);
        return targets != null && targets.size() == 1 ? targets.getFirst() : null;
    }
}
