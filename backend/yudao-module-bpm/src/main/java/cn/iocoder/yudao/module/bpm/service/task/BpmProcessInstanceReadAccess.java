package cn.iocoder.yudao.module.bpm.service.task;

import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.bpm.api.task.dto.BpmStartSubjectDTO;
import cn.iocoder.yudao.module.bpm.dal.dataobject.task.BpmProcessInstanceCopyDO;
import cn.iocoder.yudao.module.bpm.dal.mysql.task.BpmProcessInstanceCopyMapper;
import cn.iocoder.yudao.module.bpm.enums.task.BpmCommentTypeEnum;
import cn.iocoder.yudao.module.bpm.service.comment.BpmCommentService;
import cn.iocoder.yudao.module.bpm.framework.flowable.core.util.FlowableUtils;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import lombok.RequiredArgsConstructor;
import org.flowable.engine.history.HistoricProcessInstance;
import org.springframework.stereotype.Service;

import static cn.iocoder.yudao.framework.common.exception.enums.GlobalErrorCodeConstants.FORBIDDEN;
import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.bpm.enums.ErrorCodeConstants.PROCESS_INSTANCE_NOT_EXISTS;

/** Object authorization for direct reads from the Admin and Workbench BPM endpoints. */
@Service
@RequiredArgsConstructor
public class BpmProcessInstanceReadAccess {
    private final BpmProcessInstanceService processInstanceService;
    private final BpmTaskService taskService;
    private final BpmProcessInstanceCopyMapper copyMapper;
    private final PermissionApi permissionApi;
    private final BpmCommentService commentService;

    public HistoricProcessInstance requireReadable(Long userId, String instanceId) {
        HistoricProcessInstance instance = processInstanceService.getHistoricProcessInstance(instanceId);
        if (instance == null || !FlowableUtils.getTenantId().equals(instance.getTenantId())) {
            throw exception(PROCESS_INSTANCE_NOT_EXISTS);
        }
        if (userId == null) {
            throw exception(FORBIDDEN);
        }
        if (userId.equals(BpmStartSubjectDTO.getAdminUserId(instance.getStartUserId()))
                || permissionApi.hasAnyPermissions(userId, "bpm:process-instance:manager-query", "bpm:task:manager-query")) {
            return instance;
        }
        String participant = userId.toString();
        if (taskService.getTaskListByProcessInstanceId(instanceId, true).stream()
                .anyMatch(task -> participant.equals(task.getAssignee()) || participant.equals(task.getOwner()))) {
            return instance;
        }
        if (copyMapper.selectCount(new LambdaQueryWrapperX<BpmProcessInstanceCopyDO>()
                .eq(BpmProcessInstanceCopyDO::getProcessInstanceId, instanceId)
                .eq(BpmProcessInstanceCopyDO::getUserId, userId)) > 0) {
            return instance;
        }
        // Repeated transfers can leave only the first owner and the final assignee on the task.
        // Workflow-generated actions carry the authenticated actor, even when that actor was later replaced.
        if (commentService.getCommentListByProcessInstanceId(instanceId).stream()
                .anyMatch(comment -> participant.equals(comment.getUserId())
                        && (BpmCommentTypeEnum.TRANSFER.getType().equals(comment.getType())
                        || BpmCommentTypeEnum.DELEGATE_START.getType().equals(comment.getType())
                        || BpmCommentTypeEnum.DELEGATE_END.getType().equals(comment.getType())))) {
            return instance;
        }
        throw exception(FORBIDDEN);
    }
}
