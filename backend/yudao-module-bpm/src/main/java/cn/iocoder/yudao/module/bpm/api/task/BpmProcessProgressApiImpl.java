package cn.iocoder.yudao.module.bpm.api.task;

import cn.hutool.core.util.NumberUtil;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.bpm.api.task.dto.BpmProcessProgressDTO;
import cn.iocoder.yudao.module.bpm.controller.admin.task.vo.instance.BpmApprovalDetailReqVO;
import cn.iocoder.yudao.module.bpm.enums.task.BpmTaskStatusEnum;
import cn.iocoder.yudao.module.bpm.framework.flowable.core.util.FlowableUtils;
import cn.iocoder.yudao.module.bpm.service.task.BpmProcessInstanceService;
import cn.iocoder.yudao.module.bpm.service.task.BpmTaskService;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.stream.Collectors;
import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.bpm.enums.ErrorCodeConstants.PROCESS_INSTANCE_NOT_EXISTS;

@Service
public class BpmProcessProgressApiImpl implements BpmProcessProgressApi {
    @Resource private BpmTaskService taskService;
    @Resource private BpmProcessInstanceService instanceService;
    @Resource private AdminUserApi userApi;
    @Resource private org.flowable.engine.TaskService flowableTaskService;

    @Override
    public Map<String, List<BpmProcessProgressDTO.PendingTask>> getCurrentTasks(Set<String> ids) {
        if (ids.isEmpty()) return Map.of();
        String tenant = TenantContextHolder.getRequiredTenantId().toString();
        var tasks = flowableTaskService.createTaskQuery().taskTenantId(tenant)
                .processInstanceIdIn(new ArrayList<>(ids)).includeTaskLocalVariables().list().stream()
                .filter(t -> tenant.equals(t.getTenantId()) && !t.isSuspended())
                .filter(t -> Objects.equals(FlowableUtils.getTaskStatus(t), BpmTaskStatusEnum.RUNNING.getStatus()))
                .toList();
        Set<Long> userIds = tasks.stream().map(t -> NumberUtil.parseLong(t.getAssignee(), null))
                .filter(Objects::nonNull).collect(Collectors.toSet());
        var users = userApi.getUserMap(userIds);
        Map<String, List<BpmProcessProgressDTO.PendingTask>> result = new LinkedHashMap<>();
        tasks.forEach(t -> {
            Long id = NumberUtil.parseLong(t.getAssignee(), null);
            var user = id == null ? null : users.get(id);
            result.computeIfAbsent(t.getProcessInstanceId(), key -> new ArrayList<>()).add(
                    new BpmProcessProgressDTO.PendingTask(t.getId(), t.getName(), t.getTaskDefinitionKey(),
                            id, user == null ? null : user.getNickname(), time(t.getCreateTime())));
        });
        return result;
    }

    @Override
    public boolean isParticipant(String id, Long userId) {
        if (id == null || userId == null) return false;
        var instance = instanceService.getHistoricProcessInstance(id);
        if (instance == null || !TenantContextHolder.getRequiredTenantId().toString().equals(instance.getTenantId())) return false;
        String user = userId.toString();
        return taskService.getTaskListByProcessInstanceId(id, true).stream()
                .anyMatch(t -> user.equals(t.getAssignee()) || user.equals(t.getOwner()));
    }

    @Override
    public BpmProcessProgressDTO getProgress(String id, Long viewerId) {
        var instance = instanceService.getHistoricProcessInstance(id);
        if (instance == null || !TenantContextHolder.getRequiredTenantId().toString().equals(instance.getTenantId())) {
            throw exception(PROCESS_INSTANCE_NOT_EXISTS);
        }
        var detail = instanceService.getApprovalDetail(viewerId, new BpmApprovalDetailReqVO().setProcessInstanceId(id));
        var history = taskService.getTaskListByProcessInstanceId(id, true);
        var historyById = history.stream().collect(Collectors.toMap(t -> t.getId(), t -> t));
        var users = userApi.getUserMap(history.stream().flatMap(t -> java.util.stream.Stream.of(
                NumberUtil.parseLong(t.getAssignee(), null), NumberUtil.parseLong(t.getOwner(), null)))
                .filter(Objects::nonNull).collect(Collectors.toSet()));
        var nodes = Optional.ofNullable(detail.getActivityNodes()).orElse(List.of()).stream().map(node -> {
            var nodeTasks = history.stream().filter(t -> Objects.equals(node.getId(), nodeKey(t, historyById))
                    || Optional.ofNullable(node.getTasks()).orElse(List.of()).stream().anyMatch(n -> n.getId().equals(t.getId())))
                    .map(t -> {
                        Long assigneeId = NumberUtil.parseLong(t.getAssignee(), null);
                        Long ownerId = NumberUtil.parseLong(t.getOwner(), null);
                        var assignee = assigneeId == null ? null : users.get(assigneeId);
                        var owner = ownerId == null ? null : users.get(ownerId);
                        return new BpmProcessProgressDTO.Task(t.getId(), t.getParentTaskId(), FlowableUtils.getTaskStatus(t),
                                assignee == null ? null : assignee.getNickname(), owner == null ? null : owner.getNickname(),
                                time(t.getCreateTime()), time(t.getEndTime()), FlowableUtils.getTaskReason(t));
                    }).toList();
            return new BpmProcessProgressDTO.Node(node.getId(), node.getName(), node.getStatus(), node.getStartTime(),
                    node.getEndTime(), Optional.ofNullable(node.getCandidateUsers()).orElse(List.of()).stream()
                    .map(u -> new BpmProcessProgressDTO.User(u.getId(), u.getNickname())).toList(), nodeTasks);
        }).toList();
        return new BpmProcessProgressDTO(detail.getStatus(), nodes, getCurrentTasks(Set.of(id)).getOrDefault(id, List.of()));
    }

    private static LocalDateTime time(Date date) {
        return date == null ? null : LocalDateTime.ofInstant(date.toInstant(), ZoneId.systemDefault());
    }

    private static String nodeKey(org.flowable.task.api.history.HistoricTaskInstance task,
                                  Map<String, org.flowable.task.api.history.HistoricTaskInstance> history) {
        // Ad-hoc sign children may have no taskDefinitionKey; attach them to their BPM parent node.
        Set<String> visited = new HashSet<>();
        while ((task.getTaskDefinitionKey() == null || task.getTaskDefinitionKey().isBlank())
                && task.getParentTaskId() != null && visited.add(task.getId())) {
            var parent = history.get(task.getParentTaskId());
            if (parent == null) break;
            task = parent;
        }
        return task.getTaskDefinitionKey();
    }
}
