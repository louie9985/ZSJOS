package cn.iocoder.yudao.module.bpm.api.task;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.bpm.api.task.dto.BpmTaskDecisionReqDTO;
import cn.iocoder.yudao.module.bpm.api.task.dto.BpmTaskPageReqDTO;
import cn.iocoder.yudao.module.bpm.api.task.dto.BpmTaskRespDTO;
import cn.iocoder.yudao.module.bpm.api.task.dto.BpmTaskSignReqDTO;
import cn.iocoder.yudao.module.bpm.api.task.dto.BpmProcessNodeStatusRespDTO;
import jakarta.validation.constraints.NotEmpty;

/**
 * 流程任务 Api 接口
 *
 * @author jason
 */
public interface BpmProcessTaskApi {

    java.util.List<cn.iocoder.yudao.module.bpm.api.task.dto.BpmPendingTaskRespDTO> getPendingTasks(String processInstanceId);

    java.util.List<BpmProcessNodeStatusRespDTO> getProcessNodeStatuses(String processInstanceId,
                                                                         java.util.Set<String> taskDefinitionKeys);

    java.util.Map<String, java.util.List<BpmProcessNodeStatusRespDTO>> getProcessNodeStatuses(
            java.util.Set<String> processInstanceIds, java.util.Set<String> taskDefinitionKeys);

    /**
     * 触发流程任务的执行
     *
     * @param processInstanceId 流程实例编号
     * @param taskDefineKey 任务 Key
     */
    void triggerTask(@NotEmpty(message = "流程实例的编号不能为空") String processInstanceId,
                     @NotEmpty(message = "任务 Key 不能为空") String taskDefineKey);

    /** Authenticated caller must supply the effective user; returns all definitions within that user's todo scope. */
    PageResult<BpmTaskRespDTO> getMetricTodoTaskPage(Long userId, BpmTaskPageReqDTO reqDTO);
    PageResult<BpmTaskRespDTO> getMetricAllTodoTaskPage(Long userId, int pageNo, int pageSize);

    PageResult<BpmTaskRespDTO> getAllTodoTaskPage(Long userId, int pageNo, int pageSize);

    PageResult<BpmTaskRespDTO> getTodoTaskPage(Long userId, BpmTaskPageReqDTO reqDTO);

    PageResult<BpmTaskRespDTO> getDoneTaskPage(Long userId, BpmTaskPageReqDTO reqDTO);

    BpmTaskRespDTO getTodoTask(Long userId, String taskId);

    /** Tenant-scoped task held by the current actor, including an owner waiting for a required sign. */
    BpmTaskRespDTO getParticipantTodoTask(Long userId, String taskId);

    java.util.List<String> createSignTasks(Long userId, String taskId, String type,
                                         java.util.Set<Long> userIds, String reason);

    BpmTaskRespDTO getDoneTask(Long userId, String taskId);

    /** Reads committed node state after the business caller has serialized its task actions. */
    java.util.List<BpmLiveTaskState> getCommittedNodeTasks(String processInstanceId, String taskDefinitionKey);

    record BpmLiveTaskState(String id, String parentTaskId, String assignee, String scopeType, Integer status) {}

    /** Skips the named obsolete node without writing an approval or touching other branches. Caller authorizes the business operation. */
    void skipObsoleteNode(Long operatorUserId, String processInstanceId, String processDefinitionKey,
                          String businessKey, String taskDefinitionKey, String reason);

    void approveTask(Long userId, BpmTaskDecisionReqDTO reqDTO);

    void rejectTask(Long userId, BpmTaskDecisionReqDTO reqDTO);

    String createBeforeSignTask(Long userId, BpmTaskSignReqDTO reqDTO);

    String createParallelSignTask(Long userId, BpmTaskSignReqDTO reqDTO);

}
