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

    PageResult<BpmTaskRespDTO> getTodoTaskPage(Long userId, BpmTaskPageReqDTO reqDTO);

    PageResult<BpmTaskRespDTO> getDoneTaskPage(Long userId, BpmTaskPageReqDTO reqDTO);

    BpmTaskRespDTO getTodoTask(Long userId, String taskId);

    void approveTask(Long userId, BpmTaskDecisionReqDTO reqDTO);

    void rejectTask(Long userId, BpmTaskDecisionReqDTO reqDTO);

    String createBeforeSignTask(Long userId, BpmTaskSignReqDTO reqDTO);

}
