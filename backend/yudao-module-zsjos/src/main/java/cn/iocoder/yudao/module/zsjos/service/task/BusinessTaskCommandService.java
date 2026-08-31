package cn.iocoder.yudao.module.zsjos.service.task;

import cn.iocoder.yudao.module.zsjos.dal.dataobject.task.BusinessTaskDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.task.BusinessTaskMapper;
import jakarta.annotation.Resource;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Collection;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;

import static cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants.BUSINESS_TASK_COMPLETE_FORBIDDEN;
import static cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants.BUSINESS_TASK_NOT_EXISTS;
import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;

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

    public int reassignPending(Collection<String> taskTypes, Long bizId, Long assigneeId) {
        return taskMapper.reassignPending(taskTypes, bizId, assigneeId);
    }

    public boolean completeBirthdayCare(Long taskId, Long userId, LocalDateTime completedAt) {
        BusinessTaskDO task = taskMapper.selectByIdForUpdate(taskId, TenantContextHolder.getRequiredTenantId());
        if (task == null) throw exception(BUSINESS_TASK_NOT_EXISTS);
        if (!"EMPLOYEE_BIRTHDAY_CARE".equals(task.getTaskType()) || !userId.equals(task.getAssigneeId())) {
            throw exception(BUSINESS_TASK_COMPLETE_FORBIDDEN);
        }
        if ("completed".equals(task.getStatus())) return true;
        if (!"pending".equals(task.getStatus())) throw exception(BUSINESS_TASK_COMPLETE_FORBIDDEN);
        return taskMapper.completeBirthdayCare(taskId, userId, completedAt) > 0;
    }

    public boolean completeEmployeeReminder(Long taskId, Long userId, LocalDateTime completedAt) {
        BusinessTaskDO task = taskMapper.selectByIdForUpdate(taskId, TenantContextHolder.getRequiredTenantId());
        if (task == null) throw exception(BUSINESS_TASK_NOT_EXISTS);
        if (!java.util.Set.of("EMPLOYEE_BIRTHDAY_CARE", "EMPLOYEE_CONTRACT_EXPIRY", "EMPLOYEE_ENTRY_ANNIVERSARY").contains(task.getTaskType())
                || !userId.equals(task.getAssigneeId())) throw exception(BUSINESS_TASK_COMPLETE_FORBIDDEN);
        if ("completed".equals(task.getStatus())) return true;
        if (!"pending".equals(task.getStatus())) throw exception(BUSINESS_TASK_COMPLETE_FORBIDDEN);
        return taskMapper.completePending(task.getTaskType(), task.getBizId(), userId, completedAt) > 0;
    }

}
