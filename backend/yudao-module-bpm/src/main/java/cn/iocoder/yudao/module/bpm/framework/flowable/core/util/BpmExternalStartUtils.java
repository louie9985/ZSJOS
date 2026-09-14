package cn.iocoder.yudao.module.bpm.framework.flowable.core.util;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.bpm.controller.admin.definition.vo.model.simple.BpmSimpleModelNodeVO;
import cn.iocoder.yudao.module.bpm.dal.dataobject.definition.BpmProcessDefinitionInfoDO;
import cn.iocoder.yudao.module.bpm.enums.definition.BpmModelTypeEnum;
import cn.iocoder.yudao.module.bpm.enums.definition.BpmSimpleModelNodeTypeEnum;
import cn.iocoder.yudao.module.bpm.framework.flowable.core.enums.BpmTaskCandidateStrategyEnum;
import org.flowable.bpmn.model.*;

import java.util.Objects;

import static cn.iocoder.yudao.module.bpm.framework.flowable.core.enums.BpmnModelConstants.START_USER_NODE_ID;

/** Identifies the generated submission activity, never an arbitrary starter approval task. */
public final class BpmExternalStartUtils {

    private BpmExternalStartUtils() {}

    public static boolean isSubmissionTask(BpmProcessDefinitionInfoDO info, BpmnModel model, UserTask task) {
        if (info == null || !Objects.equals(info.getModelType(), BpmModelTypeEnum.SIMPLE.getType())
                || info.getSimpleModel() == null || !START_USER_NODE_ID.equals(task.getId())
                || task.getLoopCharacteristics() != null
                || !Objects.equals(BpmnModelUtils.parseCandidateStrategy(task),
                        BpmTaskCandidateStrategyEnum.START_USER.getStrategy())) {
            return false;
        }
        BpmSimpleModelNodeVO root = JsonUtils.parseObject(info.getSimpleModel(), BpmSimpleModelNodeVO.class);
        if (root == null || !START_USER_NODE_ID.equals(root.getId())
                || !Objects.equals(root.getType(), BpmSimpleModelNodeTypeEnum.START_USER_NODE.getType())) {
            return false;
        }
        // Inspect references as well as the snapshot: an identically named downstream task is not submission.
        var elements = model.getMainProcess().getFlowElements();
        var incoming = elements.stream().filter(SequenceFlow.class::isInstance).map(SequenceFlow.class::cast)
                .filter(flow -> task.getId().equals(flow.getTargetRef())).toList();
        var starts = elements.stream().filter(StartEvent.class::isInstance).toList();
        return starts.size() == 1 && incoming.size() == 1
                && starts.getFirst().getId().equals(incoming.getFirst().getSourceRef())
                && elements.stream().filter(SequenceFlow.class::isInstance).map(SequenceFlow.class::cast)
                .filter(flow -> starts.getFirst().getId().equals(flow.getSourceRef())).count() == 1;
    }
}
