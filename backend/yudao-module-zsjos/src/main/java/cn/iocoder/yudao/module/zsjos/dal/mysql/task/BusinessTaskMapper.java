package cn.iocoder.yudao.module.zsjos.dal.mysql.task;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.zsjos.controller.admin.task.vo.BusinessTaskPageReqVO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.task.BusinessTaskDO;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import org.apache.ibatis.annotations.Mapper;

import java.time.LocalDateTime;
import java.util.List;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;

import static cn.iocoder.yudao.module.zsjos.enums.LeadConstants.TASK_STATUS_PENDING;

@Mapper
public interface BusinessTaskMapper extends BaseMapperX<BusinessTaskDO> {

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

    default long selectMyPendingCount(Long assigneeId, String bucket, LocalDateTime now) {
        return selectCount(buildMyQuery(assigneeId, TASK_STATUS_PENDING, bucket, now));
    }

    default PageResult<BusinessTaskDO> selectMyPage(Long assigneeId, BusinessTaskPageReqVO reqVO,
                                                    LocalDateTime now) {
        LambdaQueryWrapperX<BusinessTaskDO> query = buildMyQuery(
                assigneeId, reqVO.getStatus(), reqVO.getBucket(), now);
        if (TASK_STATUS_PENDING.equals(reqVO.getStatus())) {
            query.orderByAsc(BusinessTaskDO::getDueAt).orderByAsc(BusinessTaskDO::getId);
        } else {
            query.orderByDesc(BusinessTaskDO::getUpdateTime).orderByDesc(BusinessTaskDO::getId);
        }
        return selectPage(reqVO, query);
    }

    private static LambdaQueryWrapperX<BusinessTaskDO> buildMyQuery(Long assigneeId, String status,
                                                                     String bucket, LocalDateTime now) {
        LambdaQueryWrapperX<BusinessTaskDO> query = new LambdaQueryWrapperX<BusinessTaskDO>()
                .eq(BusinessTaskDO::getAssigneeId, assigneeId);
        if ("done".equals(status)) {
            query.in(BusinessTaskDO::getStatus, List.of("completed", "cancelled"));
        } else {
            query.eq(BusinessTaskDO::getStatus, status);
        }
        if (bucket == null) {
            return query;
        }
        LocalDateTime tomorrow = now.toLocalDate().plusDays(1).atStartOfDay();
        switch (bucket) {
            case "unscheduled" -> query.isNull(BusinessTaskDO::getDueAt);
            case "overdue" -> query.isNotNull(BusinessTaskDO::getDueAt).lt(BusinessTaskDO::getDueAt, now);
            case "today" -> query.ge(BusinessTaskDO::getDueAt, now).lt(BusinessTaskDO::getDueAt, tomorrow);
            case "future" -> query.ge(BusinessTaskDO::getDueAt, tomorrow);
            default -> { }
        }
        return query;
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

    default int updatePending(String taskType, Long bizId, Long assigneeId, String title, String summary,
                              LocalDateTime dueAt, LocalDateTime remindAt) {
        return update(null, new LambdaUpdateWrapper<BusinessTaskDO>()
                .eq(BusinessTaskDO::getTaskType, taskType)
                .eq(BusinessTaskDO::getBizId, bizId)
                .eq(BusinessTaskDO::getStatus, TASK_STATUS_PENDING)
                .set(BusinessTaskDO::getAssigneeId, assigneeId)
                .set(BusinessTaskDO::getTitleSnapshot, title)
                .set(BusinessTaskDO::getSummarySnapshot, summary)
                .set(BusinessTaskDO::getDueAt, dueAt)
                .set(BusinessTaskDO::getRemindAt, remindAt));
    }

    default int completePendingByKey(String idempotencyKey, LocalDateTime completedAt) {
        return update(null, new LambdaUpdateWrapper<BusinessTaskDO>()
                .eq(BusinessTaskDO::getIdempotencyKey, idempotencyKey)
                .eq(BusinessTaskDO::getStatus, TASK_STATUS_PENDING)
                .set(BusinessTaskDO::getStatus, "completed")
                .set(BusinessTaskDO::getCompletedAt, completedAt));
    }
}
