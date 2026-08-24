package cn.iocoder.yudao.module.zsjos.dal.mysql.task;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.zsjos.controller.admin.task.vo.BusinessTaskPageReqVO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.task.BusinessTaskDO;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Collection;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;

import static cn.iocoder.yudao.module.zsjos.enums.LeadConstants.BIZ_TYPE_LEAD;
import static cn.iocoder.yudao.module.zsjos.enums.LeadConstants.TASK_STATUS_PENDING;
import static cn.iocoder.yudao.module.zsjos.enums.LeadConstants.TASK_TYPE_FOLLOW_UP_REMINDER;

@Mapper
public interface BusinessTaskMapper extends BaseMapperX<BusinessTaskDO> {
    @Select("SELECT * FROM zsjos_business_task WHERE id = #{id} AND tenant_id = #{tenantId} AND deleted = b'0' FOR UPDATE")
    BusinessTaskDO selectByIdForUpdate(@Param("id") Long id, @Param("tenantId") Long tenantId);

    default BusinessTaskDO selectByIdempotencyKey(String idempotencyKey) {
        return selectOne(new LambdaQueryWrapperX<BusinessTaskDO>()
                .eq(BusinessTaskDO::getIdempotencyKey, idempotencyKey));
    }

    default BusinessTaskDO selectPendingByRelationAndType(Long relationId, String taskType) {
        return selectOne(new LambdaQueryWrapperX<BusinessTaskDO>()
                .eq(BusinessTaskDO::getBizType, "student_service")
                .eq(BusinessTaskDO::getBizId, relationId)
                .eq(BusinessTaskDO::getTaskType, taskType)
                .eq(BusinessTaskDO::getStatus, TASK_STATUS_PENDING)
                .orderByDesc(BusinessTaskDO::getId).last("LIMIT 1"));
    }

    default int updatePendingDueAt(Long id, LocalDateTime dueAt) {
        return update(null, new LambdaUpdateWrapper<BusinessTaskDO>()
                .eq(BusinessTaskDO::getId, id).eq(BusinessTaskDO::getStatus, TASK_STATUS_PENDING)
                .set(BusinessTaskDO::getDueAt, dueAt).set(BusinessTaskDO::getRemindAt, dueAt)
                .setSql("version = version + 1"));
    }

    default int reassignPendingById(Long id, Long assigneeId) {
        return update(null, new LambdaUpdateWrapper<BusinessTaskDO>()
                .eq(BusinessTaskDO::getId, id).eq(BusinessTaskDO::getStatus, TASK_STATUS_PENDING)
                .set(BusinessTaskDO::getAssigneeId, assigneeId));
    }

    default List<BusinessTaskDO> selectPendingAssistanceAfter(Long lastId, int limit) {
        return selectList(new LambdaQueryWrapperX<BusinessTaskDO>()
                .eq(BusinessTaskDO::getTaskType, "student_first_contact_assistance")
                .eq(BusinessTaskDO::getStatus, TASK_STATUS_PENDING)
                .gt(BusinessTaskDO::getId, lastId)
                .orderByAsc(BusinessTaskDO::getId).last("LIMIT " + limit));
    }

    default int reassignPendingAssistance(Long id, Long previousAssigneeId, Long assigneeId, String payload) {
        return update(null, new LambdaUpdateWrapper<BusinessTaskDO>()
                .eq(BusinessTaskDO::getId, id)
                .eq(BusinessTaskDO::getTaskType, "student_first_contact_assistance")
                .eq(BusinessTaskDO::getStatus, TASK_STATUS_PENDING)
                .eq(BusinessTaskDO::getAssigneeId, previousAssigneeId)
                .set(BusinessTaskDO::getAssigneeId, assigneeId)
                .set(BusinessTaskDO::getPayload, payload));
    }

    default int completeAssistance(Long id, Long assigneeId, LocalDateTime completedAt, String payload) {
        return update(null, new LambdaUpdateWrapper<BusinessTaskDO>()
                .eq(BusinessTaskDO::getId, id).eq(BusinessTaskDO::getTaskType, "student_first_contact_assistance")
                .eq(BusinessTaskDO::getAssigneeId, assigneeId).eq(BusinessTaskDO::getStatus, TASK_STATUS_PENDING)
                .set(BusinessTaskDO::getStatus, "completed").set(BusinessTaskDO::getCompletedAt, completedAt)
                .set(BusinessTaskDO::getPayload, payload));
    }

    default BusinessTaskDO selectPendingFollowUpReminderByLeadId(Long leadId) {
        return selectOne(new LambdaQueryWrapperX<BusinessTaskDO>()
                .eq(BusinessTaskDO::getTaskType, TASK_TYPE_FOLLOW_UP_REMINDER)
                .eq(BusinessTaskDO::getBizType, BIZ_TYPE_LEAD)
                .eq(BusinessTaskDO::getBizId, leadId)
                .eq(BusinessTaskDO::getStatus, TASK_STATUS_PENDING)
                .orderByDesc(BusinessTaskDO::getId)
                .last("LIMIT 1"));
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

    default List<BusinessTaskDO> selectByAssigneeIds(List<Long> assigneeIds) {
        if (assigneeIds == null || assigneeIds.isEmpty()) {
            return List.of();
        }
        return selectList(new LambdaQueryWrapperX<BusinessTaskDO>()
                .in(BusinessTaskDO::getAssigneeId, assigneeIds));
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

    default int reassignPending(Collection<String> taskTypes, Long bizId, Long assigneeId) {
        return update(null, new LambdaUpdateWrapper<BusinessTaskDO>()
                .in(BusinessTaskDO::getTaskType, taskTypes)
                .eq(BusinessTaskDO::getBizId, bizId)
                .eq(BusinessTaskDO::getStatus, TASK_STATUS_PENDING)
                .set(BusinessTaskDO::getAssigneeId, assigneeId));
    }

    default int completeBirthdayCare(Long id, Long assigneeId, LocalDateTime completedAt) {
        return update(null, new LambdaUpdateWrapper<BusinessTaskDO>()
                .eq(BusinessTaskDO::getId, id)
                .eq(BusinessTaskDO::getTaskType, "EMPLOYEE_BIRTHDAY_CARE")
                .eq(BusinessTaskDO::getAssigneeId, assigneeId)
                .eq(BusinessTaskDO::getStatus, TASK_STATUS_PENDING)
                .set(BusinessTaskDO::getStatus, "completed")
                .set(BusinessTaskDO::getCompletedAt, completedAt));
    }
}
