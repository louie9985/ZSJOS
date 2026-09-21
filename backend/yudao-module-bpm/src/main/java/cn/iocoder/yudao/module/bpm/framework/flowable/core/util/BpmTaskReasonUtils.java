package cn.iocoder.yudao.module.bpm.framework.flowable.core.util;

import cn.iocoder.yudao.module.bpm.api.task.BpmTaskActionValidator;
import cn.iocoder.yudao.module.bpm.api.task.dto.BpmTaskActionContext;
import org.flowable.bpmn.model.BpmnModel;
import org.flowable.task.api.Task;
import org.springframework.beans.factory.ObjectProvider;

/** 查询与执行必须使用相同的有效意见规则，业务策略不改写已部署的流程定义。 */
public final class BpmTaskReasonUtils {
    private BpmTaskReasonUtils() {}

    public static Boolean approvalReasonRequired(Task task, BpmnModel model,
                                                 ObjectProvider<BpmTaskActionValidator> validators) {
        if (model == null || BpmnModelUtils.getFlowElementById(model, task.getTaskDefinitionKey()) == null) return null;
        BpmTaskActionContext context = new BpmTaskActionContext()
                .setAction(BpmTaskActionValidator.ACTION_APPROVE)
                .setTaskId(task.getId()).setParentTaskId(task.getParentTaskId())
                .setTaskDefinitionKey(task.getTaskDefinitionKey())
                .setProcessDefinitionId(task.getProcessDefinitionId())
                .setProcessDefinitionKey(model.getMainProcess().getId())
                .setProcessInstanceId(task.getProcessInstanceId()).setTenantId(task.getTenantId());
        return validators.orderedStream().map(validator -> validator.approvalReasonRequired(context))
                .filter(java.util.Objects::nonNull).findFirst()
                .orElseGet(() -> BpmnModelUtils.parseReasonRequire(model, task.getTaskDefinitionKey()));
    }
}
