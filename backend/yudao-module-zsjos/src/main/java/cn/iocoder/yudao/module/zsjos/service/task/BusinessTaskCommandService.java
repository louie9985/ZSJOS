package cn.iocoder.yudao.module.zsjos.service.task;

import cn.iocoder.yudao.module.zsjos.dal.dataobject.task.BusinessTaskDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.task.BusinessTaskMapper;
import jakarta.annotation.Resource;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

import static cn.iocoder.yudao.module.zsjos.enums.LeadConstants.ASSIGNEE_TYPE_USER;
import static cn.iocoder.yudao.module.zsjos.enums.LeadConstants.TASK_STATUS_PENDING;

@Service
public class BusinessTaskCommandService {

    @Resource
    private BusinessTaskMapper taskMapper;

    public Long create(BusinessTaskCreateCommand command) {
        if (command.idempotencyKey() != null) {
            BusinessTaskDO existing = taskMapper.selectByIdempotencyKey(command.idempotencyKey());
            if (existing != null) {
                return existing.getId();
            }
        }
        BusinessTaskDO task = new BusinessTaskDO();
        task.setTaskType(command.taskType());
        task.setBizType(command.bizType());
        task.setBizId(command.bizId());
        task.setStatus(TASK_STATUS_PENDING);
        task.setAssigneeType(ASSIGNEE_TYPE_USER);
        task.setAssigneeId(command.assigneeId());
        task.setTitleSnapshot(command.title());
        task.setSummarySnapshot(command.summary());
        task.setActionCode(command.actionCode());
        task.setDueAt(command.dueAt());
        task.setRemindAt(command.remindAt());
        task.setPayload(command.payload());
        task.setIdempotencyKey(command.idempotencyKey());
        task.setVersion(0);
        try {
            taskMapper.insert(task);
        } catch (DuplicateKeyException duplicate) {
            if (command.idempotencyKey() == null) throw duplicate;
            BusinessTaskDO existing = taskMapper.selectByIdempotencyKey(command.idempotencyKey());
            if (existing == null) throw duplicate;
            return existing.getId();
        }
        return task.getId();
    }

    public boolean complete(String taskType, Long bizId, Long assigneeId, LocalDateTime completedAt) {
        return taskMapper.completePending(taskType, bizId, assigneeId, completedAt) > 0;
    }

    public boolean completeByKey(String idempotencyKey, LocalDateTime completedAt) {
        return taskMapper.completePendingByKey(idempotencyKey, completedAt) > 0;
    }

    public int cancel(String taskType, Long bizId, Long assigneeId, LocalDateTime cancelledAt, String reason) {
        return taskMapper.cancelPending(taskType, bizId, assigneeId, cancelledAt, reason);
    }

    public int updatePending(String taskType, Long bizId, Long assigneeId, String title, String summary,
                             LocalDateTime dueAt, LocalDateTime remindAt) {
        return taskMapper.updatePending(taskType, bizId, assigneeId, title, summary, dueAt, remindAt);
    }

    public BusinessTaskDO getByIdempotencyKey(String idempotencyKey) {
        return taskMapper.selectByIdempotencyKey(idempotencyKey);
    }

}
