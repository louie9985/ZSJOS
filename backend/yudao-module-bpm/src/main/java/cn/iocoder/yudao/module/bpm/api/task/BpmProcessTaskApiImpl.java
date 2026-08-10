package cn.iocoder.yudao.module.bpm.api.task;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.bpm.api.task.dto.BpmTaskDecisionReqDTO;
import cn.iocoder.yudao.module.bpm.api.task.dto.BpmTaskPageReqDTO;
import cn.iocoder.yudao.module.bpm.api.task.dto.BpmTaskRespDTO;
import cn.iocoder.yudao.module.bpm.controller.admin.task.vo.task.BpmTaskApproveReqVO;
import cn.iocoder.yudao.module.bpm.controller.admin.task.vo.task.BpmTaskPageReqVO;
import cn.iocoder.yudao.module.bpm.controller.admin.task.vo.task.BpmTaskRejectReqVO;
import cn.iocoder.yudao.module.bpm.service.task.BpmProcessInstanceService;
import cn.iocoder.yudao.module.bpm.service.task.BpmTaskService;
import jakarta.annotation.Resource;
import org.flowable.engine.history.HistoricProcessInstance;
import org.flowable.engine.runtime.ProcessInstance;
import org.flowable.task.api.Task;
import org.flowable.task.api.history.HistoricTaskInstance;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

import java.util.Map;
import java.util.Set;
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

    @Override
    public void triggerTask(String processInstanceId, String taskDefineKey) {
        bpmTaskService.triggerTask(processInstanceId, taskDefineKey);
    }

    @Override
    public PageResult<BpmTaskRespDTO> getTodoTaskPage(Long userId, BpmTaskPageReqDTO reqDTO) {
        PageResult<Task> page = bpmTaskService.getTaskTodoPage(userId, toPageReq(reqDTO));
        Set<String> processIds = convertSet(page.getList(), Task::getProcessInstanceId);
        Map<String, ProcessInstance> processes = processInstanceService.getProcessInstanceMap(processIds);
        java.util.List<BpmTaskRespDTO> list = page.getList().stream().map(task -> {
            ProcessInstance process = processes.get(task.getProcessInstanceId());
            BpmTaskRespDTO result = new BpmTaskRespDTO();
            result.setId(task.getId()); result.setProcessInstanceId(task.getProcessInstanceId());
            result.setBusinessKey(process == null ? null : process.getBusinessKey());
            result.setTaskDefinitionKey(task.getTaskDefinitionKey()); result.setCreateTime(toLocalDateTime(task.getCreateTime()));
            return result;
        }).toList();
        return new PageResult<>(list, page.getTotal());
    }

    @Override
    public PageResult<BpmTaskRespDTO> getDoneTaskPage(Long userId, BpmTaskPageReqDTO reqDTO) {
        PageResult<HistoricTaskInstance> page = bpmTaskService.getTaskDonePage(userId, toPageReq(reqDTO));
        Set<String> processIds = convertSet(page.getList(), HistoricTaskInstance::getProcessInstanceId);
        Map<String, HistoricProcessInstance> processes = processInstanceService.getHistoricProcessInstanceMap(processIds);
        java.util.List<BpmTaskRespDTO> list = page.getList().stream().map(task -> {
            HistoricProcessInstance process = processes.get(task.getProcessInstanceId());
            BpmTaskRespDTO result = new BpmTaskRespDTO();
            result.setId(task.getId()); result.setProcessInstanceId(task.getProcessInstanceId());
            result.setBusinessKey(process == null ? null : process.getBusinessKey());
            result.setTaskDefinitionKey(task.getTaskDefinitionKey());
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

    private BpmTaskPageReqVO toPageReq(BpmTaskPageReqDTO reqDTO) {
        BpmTaskPageReqVO result = new BpmTaskPageReqVO();
        result.setPageNo(reqDTO.getPageNo()); result.setPageSize(reqDTO.getPageSize());
        result.setProcessDefinitionKey(reqDTO.getProcessDefinitionKey()); return result;
    }

    private LocalDateTime toLocalDateTime(Date value) {
        return value == null ? null : LocalDateTime.ofInstant(value.toInstant(), ZoneId.systemDefault());
    }

}
