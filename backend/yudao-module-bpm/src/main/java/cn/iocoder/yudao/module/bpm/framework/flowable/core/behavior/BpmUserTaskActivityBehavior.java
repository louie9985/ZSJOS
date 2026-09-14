package cn.iocoder.yudao.module.bpm.framework.flowable.core.behavior;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.convert.Convert;
import cn.hutool.core.util.RandomUtil;
import cn.iocoder.yudao.module.bpm.framework.flowable.core.candidate.BpmTaskCandidateInvoker;
import cn.iocoder.yudao.module.bpm.api.task.dto.BpmStartSubjectDTO;
import cn.iocoder.yudao.framework.common.enums.UserTypeEnum;
import cn.iocoder.yudao.module.bpm.framework.flowable.core.util.BpmExternalStartUtils;
import cn.iocoder.yudao.module.bpm.framework.flowable.core.util.FlowableUtils;
import cn.iocoder.yudao.module.bpm.service.definition.BpmProcessDefinitionService;
import cn.iocoder.yudao.module.bpm.framework.flowable.core.enums.BpmnVariableConstants;
import org.flowable.engine.impl.util.ProcessDefinitionUtil;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.flowable.bpmn.model.UserTask;
import org.flowable.common.engine.impl.el.ExpressionManager;
import org.flowable.engine.delegate.DelegateExecution;
import org.flowable.engine.impl.bpmn.behavior.UserTaskActivityBehavior;
import org.flowable.engine.impl.cfg.ProcessEngineConfigurationImpl;
import org.flowable.engine.impl.persistence.entity.ProcessDefinitionEntity;
import org.flowable.engine.impl.util.CommandContextUtil;
import org.flowable.engine.impl.util.TaskHelper;
import org.flowable.engine.interceptor.CreateUserTaskBeforeContext;
import org.flowable.task.service.TaskService;
import org.flowable.task.service.impl.persistence.entity.TaskEntity;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.bpm.enums.ErrorCodeConstants.PROCESS_INSTANCE_EXTERNAL_CANDIDATE_UNSUPPORTED;

/**
 * 自定义的【单个】流程任务的 assignee 负责人的分配
 * 第一步，基于分配规则，计算出分配任务的【单个】候选人。如果找不到，则直接报业务异常，不继续执行后续的流程；
 * 第二步，随机选择一个候选人，则选择作为 assignee 负责人。
 *
 * @author 芋道源码
 */
@Slf4j
public class BpmUserTaskActivityBehavior extends UserTaskActivityBehavior {

    @Setter
    private BpmTaskCandidateInvoker taskCandidateInvoker;

    @Setter
    private BpmProcessDefinitionService processDefinitionService;

    public BpmUserTaskActivityBehavior(UserTask userTask) {
        super(userTask);
    }

    @Override
    public void execute(DelegateExecution execution) {
        // Use persisted engine identity, not mutable process variables or the current operator.
        var instance = CommandContextUtil.getExecutionEntityManager().findById(execution.getProcessInstanceId());
        if (instance == null) {
            super.execute(execution);
            return;
        }
        var subject = BpmStartSubjectDTO.fromFlowableId(instance.getStartUserId());
        if (subject != null && !UserTypeEnum.ADMIN.getValue().equals(subject.getUserType())
                && processDefinitionService != null
                // The definition lookup is tenant-scoped; an async engine thread loses tenant context,
                // so wrap it as BpmTaskCandidateInvoker does to keep the external check from failing open.
                && FlowableUtils.execute(execution.getTenantId(), () -> BpmExternalStartUtils.isSubmissionTask(
                        processDefinitionService.getProcessDefinitionInfo(execution.getProcessDefinitionId()),
                        ProcessDefinitionUtil.getBpmnModel(execution.getProcessDefinitionId()), userTask))) {
            // A return-to-submitter requires an external editing contract; never silently resubmit it.
            if (Boolean.TRUE.equals(execution.getVariable(String.format(
                    BpmnVariableConstants.PROCESS_INSTANCE_VARIABLE_RETURN_FLAG, userTask.getId())))
                    || Boolean.FALSE.equals(Convert.toBool(execution.getVariable(
                            BpmnVariableConstants.PROCESS_INSTANCE_VARIABLE_SKIP_START_USER_NODE)))) {
                throw exception(PROCESS_INSTANCE_EXTERNAL_CANDIDATE_UNSUPPORTED);
            }
            // Keep activity history, but never create a Partner task or invoke empty-ADMIN-candidate policies.
            leave(execution);
            return;
        }
        super.execute(execution);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    protected void handleAssignments(TaskService taskService, String assignee, String owner,
        List<String> candidateUsers, List<String> candidateGroups, TaskEntity task, ExpressionManager expressionManager,
        DelegateExecution execution, ProcessEngineConfigurationImpl processEngineConfiguration) {
        // 第一步，获得任务的候选用户
        Long assigneeUserId = calculateTaskCandidateUsers(execution);
        // 第二步，设置作为负责人
        if (assigneeUserId != null) {
            TaskHelper.changeTaskAssignee(task, String.valueOf(assigneeUserId));
        }
    }

    private Long calculateTaskCandidateUsers(DelegateExecution execution) {
        // 情况一，如果是多实例的任务，例如说会签、或签等情况，则从 Variable 中获取。
        // 顺序审批可见 BpmSequentialMultiInstanceBehavior，并发审批可见 BpmSequentialMultiInstanceBehavior
        if (super.multiInstanceActivityBehavior != null) {
            return execution.getVariable(super.multiInstanceActivityBehavior.getCollectionElementVariable(), Long.class);
        }

        // 情况二，如果非多实例的任务，则计算任务处理人
        // 第一步，先计算可处理该任务的处理人们
        Set<Long> candidateUserIds = taskCandidateInvoker.calculateUsersByTask(execution);
        if (CollUtil.isEmpty(candidateUserIds)) {
            return null;
        }
        // 第二步，后随机选择一个任务的处理人
        // 疑问：为什么一定要选择一个任务处理人？
        // 解答：项目对 bpm 的任务是责任到人，所以每个任务有且仅有一个处理人。
        //      如果希望一个任务可以同时被多个人处理，可以考虑使用 BpmParallelMultiInstanceBehavior 实现的会签 or 或签。
        int index = RandomUtil.randomInt(candidateUserIds.size());
        return CollUtil.get(candidateUserIds, index);
    }

    @Override
    protected void handleCategory(CreateUserTaskBeforeContext beforeContext, ExpressionManager expressionManager,
                                  TaskEntity task, DelegateExecution execution) {
        ProcessDefinitionEntity processDefinitionEntity = CommandContextUtil.getProcessDefinitionEntityManager().findById(execution.getProcessDefinitionId());
        if (processDefinitionEntity == null) {
            log.warn("[handleCategory][任务编号({}) 找不到流程定义({})]", task.getId(), execution.getProcessDefinitionId());
            return;
        }
        task.setCategory(processDefinitionEntity.getCategory());
    }

}
