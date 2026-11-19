package cn.iocoder.yudao.module.bpm.service.comment;

import cn.iocoder.yudao.module.bpm.controller.admin.comment.vo.BpmCommentCreateReqVO;
import cn.iocoder.yudao.module.bpm.enums.task.BpmCommentTypeEnum;
import cn.iocoder.yudao.module.bpm.service.task.BpmTaskService;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.flowable.engine.TaskService;
import org.flowable.engine.task.Comment;
import org.flowable.task.api.Task;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.bpm.enums.ErrorCodeConstants.TASK_OPERATE_FAIL_ASSIGN_NOT_SELF;

/**
 * 流程评论 Service 实现类
 *
 * @author 芋道源码
 */
@Service
public class BpmCommentServiceImpl implements BpmCommentService {

    @Resource
    private TaskService taskService;
    @Resource
    @Lazy // 延迟加载，避免循环依赖
    private BpmTaskService bpmTaskService;

    @Override
    public List<Comment> getCommentListByProcessInstanceId(String processInstanceId) {
        return taskService.getProcessInstanceComments(processInstanceId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void createComment(Long userId, @Valid BpmCommentCreateReqVO reqVO) {
        Task task = bpmTaskService.validateTask(userId, reqVO.getTaskId());
        if (userId == null || !userId.toString().equals(task.getAssignee())) {
            throw exception(TASK_OPERATE_FAIL_ASSIGN_NOT_SELF);
        }
        createComment(task.getId(), task.getProcessInstanceId(), BpmCommentTypeEnum.COMMENT, reqVO.getMessage());
    }

    @Override
    public void createComment(String taskId, String processInstanceId, BpmCommentTypeEnum type, Object... params) {
        taskService.addComment(taskId, processInstanceId, type.getType(), type.formatComment(params));
    }

}
