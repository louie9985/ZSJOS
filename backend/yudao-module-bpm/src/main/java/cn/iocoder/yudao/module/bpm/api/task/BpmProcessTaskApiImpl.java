package cn.iocoder.yudao.module.bpm.api.task;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.bpm.api.task.dto.BpmTaskDecisionReqDTO;
import cn.iocoder.yudao.module.bpm.api.task.dto.BpmTaskPageReqDTO;
import cn.iocoder.yudao.module.bpm.api.task.dto.BpmTaskRespDTO;
import cn.iocoder.yudao.module.bpm.api.task.dto.BpmTaskSignReqDTO;
import cn.iocoder.yudao.module.bpm.api.task.dto.BpmProcessNodeStatusRespDTO;
import cn.iocoder.yudao.module.bpm.controller.admin.task.vo.task.BpmTaskApproveReqVO;
import cn.iocoder.yudao.module.bpm.controller.admin.task.vo.task.BpmTaskPageReqVO;
import cn.iocoder.yudao.module.bpm.controller.admin.task.vo.task.BpmTaskRejectReqVO;
import cn.iocoder.yudao.module.bpm.controller.admin.task.vo.task.BpmTaskSignCreateReqVO;
import cn.iocoder.yudao.module.bpm.enums.task.BpmTaskSignTypeEnum;
import cn.iocoder.yudao.module.bpm.service.task.BpmProcessInstanceService;
import cn.iocoder.yudao.module.bpm.service.task.BpmTaskService;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import cn.iocoder.yudao.module.system.api.user.dto.AdminUserRespDTO;
import cn.hutool.core.util.NumberUtil;
import jakarta.annotation.Resource;
import org.flowable.engine.history.HistoricProcessInstance;
import org.flowable.engine.runtime.ProcessInstance;
import org.flowable.task.api.Task;
import org.flowable.task.api.history.HistoricTaskInstance;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

import java.util.Map;
import java.util.Set;
import java.util.List;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Date;
import java.time.LocalDateTime;
import java.time.ZoneId;

import static cn.iocoder.yudao.framework.common.util.collection.CollectionUtils.convertSet;

/**
 * 流程任务 Api 实现类
 *
 * @author jason
 */
@Service
@Validated
public class BpmProcessTaskApiImpl implements BpmProcessTaskApi {

    @Resource
    private BpmTaskService bpmTaskService;
    @Resource
    private BpmProcessInstanceService processInstanceService;
    @Resource
    private AdminUserApi adminUserApi;

    @Override
    public List<BpmProcessNodeStatusRespDTO> getProcessNodeStatuses(String processInstanceId, Set<String> taskDefinitionKeys) {
        if (processInstanceId == null || taskDefinitionKeys == null || taskDefinitionKeys.isEmpty()) return List.of();
        Map<String, BpmProcessNodeStatusRespDTO> result = new HashMap<>();
        for (Task task : bpmTaskService.getRunningTaskListByProcessInstanceId(processInstanceId, null, null)) {
            if (!taskDefinitionKeys.contains(task.getTaskDefinitionKey()) || task.getParentTaskId() != null) continue;
            BpmProcessNodeStatusRespDTO status = new BpmProcessNodeStatusRespDTO();
            status.setTaskDefinitionKey(task.getTaskDefinitionKey()); status.setStatus("pending");
            status.setCreateTime(toLocalDateTime(task.getCreateTime()));
            result.put(task.getTaskDefinitionKey(), status);
        }
        for (HistoricTaskInstance task : bpmTaskService.getTaskListByProcessInstanceId(processInstanceId, true)) {
            if (!taskDefinitionKeys.contains(task.getTaskDefinitionKey()) || task.getParentTaskId() != null
                    || task.getEndTime() == null) continue;
            Integer rawStatus = task.getTaskLocalVariables() == null ? null
                    : (Integer) task.getTaskLocalVariables().get(cn.iocoder.yudao.module.bpm.framework.flowable.core.enums.BpmnVariableConstants.TASK_VARIABLE_STATUS);
            String status = rawStatus == null ? "cancelled" : switch (rawStatus) {
                case 2, 7 -> "approved";
                case 3, 5 -> "rejected";
                case 4 -> "cancelled";
                default -> "pending";
            };
            BpmProcessNodeStatusRespDTO current = result.get(task.getTaskDefinitionKey());
            LocalDateTime taskEndTime = toLocalDateTime(task.getEndTime());
            if (current == null || statusRank(status) > statusRank(current.getStatus())
                    || statusRank(status) == statusRank(current.getStatus())
                    && (current.getEndTime() == null || current.getEndTime().isBefore(taskEndTime))) {
                BpmProcessNodeStatusRespDTO value = new BpmProcessNodeStatusRespDTO();
                value.setTaskDefinitionKey(task.getTaskDefinitionKey()); value.setStatus(status);
                value.setReviewerUserId(NumberUtil.parseLong(task.getAssignee(), null));
                value.setCreateTime(toLocalDateTime(task.getCreateTime())); value.setEndTime(taskEndTime);
                result.put(task.getTaskDefinitionKey(), value);
            }
        }
        Set<Long> reviewerUserIds = result.values().stream().map(BpmProcessNodeStatusRespDTO::getReviewerUserId)
                .filter(java.util.Objects::nonNull).collect(java.util.stream.Collectors.toSet());
        if (!reviewerUserIds.isEmpty()) {
            Map<Long, AdminUserRespDTO> reviewerUsers = adminUserApi.getUserMap(reviewerUserIds);
            result.values().forEach(item -> {
                AdminUserRespDTO reviewer = reviewerUsers.get(item.getReviewerUserId());
                item.setReviewerUserName(reviewer == null ? null : reviewer.getNickname());
            });
        }
        return result.values().stream().sorted(Comparator.comparing(BpmProcessNodeStatusRespDTO::getTaskDefinitionKey)).toList();
    }

    private int statusRank(String status) {
        return switch (status) {
            case "rejected" -> 4;
            case "approved" -> 3;
            case "pending" -> 2;
            case "cancelled" -> 1;
            default -> 0;
        };
    }

    @Override
    public void triggerTask(String processInstanceId, String taskDefineKey) {
        bpmTaskService.triggerTask(processInstanceId, taskDefineKey);
    }

    @Override
    public PageResult<BpmTaskRespDTO> getTodoTaskPage(Long userId, BpmTaskPageReqDTO reqDTO) {
        PageResult<Task> page = bpmTaskService.getTaskTodoPage(userId, toPageReq(reqDTO),
                reqDTO.getProcessVariableName(), reqDTO.getProcessVariableValues());
        if (page.getList().isEmpty()) {
            return PageResult.empty(page.getTotal());
        }
        Set<String> processIds = convertSet(page.getList(), Task::getProcessInstanceId);
        Map<String, ProcessInstance> processes = processInstanceService.getProcessInstanceMap(processIds);
        java.util.List<BpmTaskRespDTO> list = page.getList().stream().map(task -> {
            ProcessInstance process = processes.get(task.getProcessInstanceId());
            BpmTaskRespDTO result = new BpmTaskRespDTO();
            result.setId(task.getId()); result.setProcessInstanceId(task.getProcessInstanceId());
            result.setBusinessKey(process == null ? null : process.getBusinessKey());
            result.setTaskDefinitionKey(task.getTaskDefinitionKey()); result.setCreateTime(toLocalDateTime(task.getCreateTime()));
            result.setParentTaskId(task.getParentTaskId()); result.setSignTask(task.getParentTaskId() != null);
            return result;
        }).toList();
        return new PageResult<>(list, page.getTotal());
    }

    @Override
    public PageResult<BpmTaskRespDTO> getDoneTaskPage(Long userId, BpmTaskPageReqDTO reqDTO) {
        PageResult<HistoricTaskInstance> page = bpmTaskService.getTaskDonePage(userId, toPageReq(reqDTO),
                reqDTO.getProcessVariableName(), reqDTO.getProcessVariableValues());
        if (page.getList().isEmpty()) {
            return PageResult.empty(page.getTotal());
        }
        Set<String> processIds = convertSet(page.getList(), HistoricTaskInstance::getProcessInstanceId);
        Map<String, HistoricProcessInstance> processes = processInstanceService.getHistoricProcessInstanceMap(processIds);
        java.util.List<BpmTaskRespDTO> list = page.getList().stream().map(task -> {
            HistoricProcessInstance process = processes.get(task.getProcessInstanceId());
            BpmTaskRespDTO result = new BpmTaskRespDTO();
            result.setId(task.getId()); result.setProcessInstanceId(task.getProcessInstanceId());
            result.setBusinessKey(process == null ? null : process.getBusinessKey());
            result.setTaskDefinitionKey(task.getTaskDefinitionKey());
            result.setParentTaskId(task.getParentTaskId()); result.setSignTask(task.getParentTaskId() != null);
            result.setStatus(task.getTaskLocalVariables() == null ? null : (Integer) task.getTaskLocalVariables().get("status"));
            result.setReason(task.getDescription()); result.setCreateTime(toLocalDateTime(task.getCreateTime()));
            result.setEndTime(toLocalDateTime(task.getEndTime())); return result;
        }).toList();
        return new PageResult<>(list, page.getTotal());
    }

    @Override
    public BpmTaskRespDTO getTodoTask(Long userId, String taskId) {
        Task task = bpmTaskService.validateTask(userId, taskId);
        ProcessInstance process = processInstanceService.getProcessInstance(task.getProcessInstanceId());
        BpmTaskRespDTO result = new BpmTaskRespDTO();
        result.setId(task.getId()); result.setProcessInstanceId(task.getProcessInstanceId());
        result.setBusinessKey(process == null ? null : process.getBusinessKey());
        result.setTaskDefinitionKey(task.getTaskDefinitionKey()); result.setCreateTime(toLocalDateTime(task.getCreateTime()));
        result.setParentTaskId(task.getParentTaskId()); result.setSignTask(task.getParentTaskId() != null);
        return result;
    }

    @Override
    public void approveTask(Long userId, BpmTaskDecisionReqDTO reqDTO) {
        bpmTaskService.approveTask(userId, new BpmTaskApproveReqVO().setId(reqDTO.getTaskId())
                .setReason(reqDTO.getReason()).setAttachments(reqDTO.getAttachments()));
    }

    @Override
    public void rejectTask(Long userId, BpmTaskDecisionReqDTO reqDTO) {
        bpmTaskService.rejectTask(userId, new BpmTaskRejectReqVO().setId(reqDTO.getTaskId())
                .setReason(reqDTO.getReason()).setAttachments(reqDTO.getAttachments()));
    }

    @Override
    public String createBeforeSignTask(Long userId, BpmTaskSignReqDTO reqDTO) {
        List<String> taskIds = bpmTaskService.createSignTask(userId, new BpmTaskSignCreateReqVO()
                .setId(reqDTO.getTaskId()).setUserIds(Set.of(reqDTO.getAssigneeUserId()))
                .setType(BpmTaskSignTypeEnum.BEFORE.getType()).setReason(reqDTO.getReason()));
        return taskIds.get(0);
    }

    private BpmTaskPageReqVO toPageReq(BpmTaskPageReqDTO reqDTO) {
        BpmTaskPageReqVO result = new BpmTaskPageReqVO();
        result.setPageNo(reqDTO.getPageNo()); result.setPageSize(reqDTO.getPageSize());
        result.setProcessDefinitionKey(reqDTO.getProcessDefinitionKey());
        result.setTaskDefinitionKey(reqDTO.getTaskDefinitionKey());
        result.setProcessInstanceIds(reqDTO.getProcessInstanceIds());
        return result;
    }

    private LocalDateTime toLocalDateTime(Date value) {
        return value == null ? null : LocalDateTime.ofInstant(value.toInstant(), ZoneId.systemDefault());
    }

}
