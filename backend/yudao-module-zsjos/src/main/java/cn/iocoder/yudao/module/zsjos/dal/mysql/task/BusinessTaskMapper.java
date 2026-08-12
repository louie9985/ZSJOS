package cn.iocoder.yudao.module.zsjos.dal.mysql.task;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.task.BusinessTaskDO;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;

import static cn.iocoder.yudao.module.zsjos.enums.LeadConstants.TASK_STATUS_PENDING;

@Mapper
public interface BusinessTaskMapper extends BaseMapperX<BusinessTaskDO> {
    @Select("SELECT * FROM zsjos_business_task WHERE id = #{id} AND tenant_id = #{tenantId} AND deleted = b'0' FOR UPDATE")
    BusinessTaskDO selectByIdForUpdate(@Param("id") Long id, @Param("tenantId") Long tenantId);

    default BusinessTaskDO selectByIdempotencyKey(String idempotencyKey) {
        return selectOne(new LambdaQueryWrapperX<BusinessTaskDO>()
                .eq(BusinessTaskDO::getIdempotencyKey, idempotencyKey));
    }

    default List<BusinessTaskDO> selectExpiredPending(String taskType, LocalDateTime now, int limit) {
        return selectList(new LambdaQueryWrapperX<BusinessTaskDO>()
                .eq(BusinessTaskDO::getTaskType, taskType)
                .eq(BusinessTaskDO::getStatus, TASK_STATUS_PENDING)
                .le(BusinessTaskDO::getDueAt, now)
                .orderByAsc(BusinessTaskDO::getDueAt)
                .last("LIMIT " + limit));
    }

    default List<BusinessTaskDO> selectMyPending(Long assigneeId) {
        return selectList(new LambdaQueryWrapperX<BusinessTaskDO>()
                .eq(BusinessTaskDO::getAssigneeId, assigneeId)
                .eq(BusinessTaskDO::getStatus, TASK_STATUS_PENDING)
                .orderByAsc(BusinessTaskDO::getDueAt)
                .orderByAsc(BusinessTaskDO::getId));
    }

    default List<BusinessTaskDO> selectByAssigneeIds(List<Long> assigneeIds) {
        if (assigneeIds == null || assigneeIds.isEmpty()) return List.of();
        return selectList(new LambdaQueryWrapperX<BusinessTaskDO>().in(BusinessTaskDO::getAssigneeId, assigneeIds));
    }

    default List<BusinessTaskDO> selectPendingReminderCandidates(List<String> taskTypes,
                                                                  LocalDateTime latestDueAt, int limit) {
        return selectList(new LambdaQueryWrapperX<BusinessTaskDO>()
                .in(BusinessTaskDO::getTaskType, taskTypes)
                .eq(BusinessTaskDO::getStatus, TASK_STATUS_PENDING)
                .isNotNull(BusinessTaskDO::getDueAt)
                .le(BusinessTaskDO::getDueAt, latestDueAt)
                .orderByAsc(BusinessTaskDO::getDueAt)
                .last("LIMIT " + limit));
    }

    default int completePending(String taskType, Long bizId, Long assigneeId, LocalDateTime completedAt) {
        return update(null, new LambdaUpdateWrapper<BusinessTaskDO>()
                .eq(BusinessTaskDO::getTaskType, taskType)
                .eq(BusinessTaskDO::getBizId, bizId)
                .eq(BusinessTaskDO::getAssigneeId, assigneeId)
                .eq(BusinessTaskDO::getStatus, TASK_STATUS_PENDING)
                .set(BusinessTaskDO::getStatus, "completed")
                .set(BusinessTaskDO::getCompletedAt, completedAt));
    }

    default int cancelPending(String taskType, Long bizId, Long assigneeId,
                              LocalDateTime cancelledAt, String reason) {
        return update(null, new LambdaUpdateWrapper<BusinessTaskDO>()
                .eq(BusinessTaskDO::getTaskType, taskType)
                .eq(BusinessTaskDO::getBizId, bizId)
                .eq(assigneeId != null, BusinessTaskDO::getAssigneeId, assigneeId)
                .eq(BusinessTaskDO::getStatus, TASK_STATUS_PENDING)
                .set(BusinessTaskDO::getStatus, "cancelled")
                .set(BusinessTaskDO::getCancelledAt, cancelledAt)
                .set(BusinessTaskDO::getCancelReason, reason));
    }

    default int completePendingByKey(String idempotencyKey, LocalDateTime completedAt) {
        return update(null, new LambdaUpdateWrapper<BusinessTaskDO>()
                .eq(BusinessTaskDO::getIdempotencyKey, idempotencyKey)
                .eq(BusinessTaskDO::getStatus, TASK_STATUS_PENDING)
                .set(BusinessTaskDO::getStatus, "completed")
                .set(BusinessTaskDO::getCompletedAt, completedAt));
    }
}
