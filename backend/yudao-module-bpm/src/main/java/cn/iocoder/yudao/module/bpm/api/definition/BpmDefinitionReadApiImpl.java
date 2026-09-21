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
import org.flowable.bpmn.model.EndEvent;
import org.flowable.bpmn.model.FlowElement;
import org.flowable.bpmn.model.SequenceFlow;
import org.flowable.bpmn.model.StartEvent;
import org.flowable.bpmn.model.UserTask;
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
        result.setUserTasks(userTasks.stream().map(task -> {
                    BpmUserTaskMetadataRespDTO userTask = new BpmUserTaskMetadataRespDTO();
                    userTask.setKey(task.getId());
                    userTask.setName(task.getName());
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

    private String onlyTarget(Map<String, List<String>> outgoing, String source) {
        List<String> targets = source == null ? null : outgoing.get(source);
        return targets != null && targets.size() == 1 ? targets.getFirst() : null;
    }
}
