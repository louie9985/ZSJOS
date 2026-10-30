package cn.iocoder.yudao.module.zsjos.dal.mysql.task;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.zsjos.controller.admin.task.vo.BusinessTaskPageReqVO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.task.BusinessTaskDO;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import cn.iocoder.yudao.module.zsjos.dal.mysql.lead.LeadFollowUpResponsibilitySql;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Collection;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;

import static cn.iocoder.yudao.module.zsjos.enums.LeadConstants.BIZ_TYPE_LEAD;
import static cn.iocoder.yudao.module.zsjos.enums.LeadConstants.TASK_STATUS_PENDING;
import static cn.iocoder.yudao.module.zsjos.enums.LeadConstants.TASK_TYPE_FOLLOW_UP_REMINDER;

@Mapper
public interface BusinessTaskMapper extends BaseMapperX<BusinessTaskDO> {
    @lombok.Data
    class EffectiveTaskAssignee {
        private Long id;
        private Long assigneeId;
    }

    @org.apache.ibatis.annotations.SelectProvider(type = LeadFollowUpResponsibilitySql.class, method = "taskAssignees")
    List<EffectiveTaskAssignee> selectEffectiveTaskAssignees(
            @Param("ids") List<Long> ids, @Param("tenantId") Long tenantId);

    default void applyCurrentFollowUpAssignees(List<BusinessTaskDO> tasks) {
        List<BusinessTaskDO> following = tasks.stream().filter(t -> BIZ_TYPE_LEAD.equals(t.getBizType())
                && TASK_STATUS_PENDING.equals(t.getStatus())
                && List.of("lead_first_follow_up", TASK_TYPE_FOLLOW_UP_REMINDER).contains(t.getTaskType())).toList();
        if (following.isEmpty()) return;
        var resolved = selectEffectiveTaskAssignees(following.stream().map(BusinessTaskDO::getId).toList(),
                cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder.getRequiredTenantId());
        java.util.Map<Long, Long> assignees = new java.util.HashMap<>();
        resolved.forEach(row -> assignees.put(row.getId(), row.getAssigneeId()));
        following.forEach(task -> task.setAssigneeId(assignees.get(task.getId())));
    }

    @org.apache.ibatis.annotations.Update("UPDATE zsjos_business_task SET status='cancelled', cancelled_at=#{now}, cancel_reason=#{reason}, version=version+1 WHERE tenant_id=(SELECT tenant_id FROM zsjos_media_account WHERE id=#{accountId}) AND deleted=b'0' AND status='pending' AND ((biz_type='media_account_diagnosis' AND biz_id=#{accountId}) OR JSON_UNQUOTE(JSON_EXTRACT(payload,'$.accountId'))=CAST(#{accountId} AS CHAR))")
    int cancelByAccountId(@Param("accountId") Long accountId, @Param("now") LocalDateTime now, @Param("reason") String reason);
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

    @org.apache.ibatis.annotations.Update("UPDATE zsjos_business_task SET status='cancelled', cancelled_at=#{now}, cancel_reason=#{reason}, version=version+1 WHERE tenant_id=#{tenantId} AND deleted=0 AND status='pending' AND biz_type='student_service' AND biz_id=#{relationId} AND task_type IN ('student_first_contact','student_study_plan','student_contact','student_delivery_stage','student_first_contact_assistance')")
    int cancelTeachingService(@Param("relationId") Long relationId,@Param("tenantId") Long tenantId,@Param("now") LocalDateTime now,@Param("reason") String reason);

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

    default List<BusinessTaskDO> selectPendingFollowUpRemindersByLeadIds(java.util.Collection<Long> leadIds) {
        if (leadIds.isEmpty()) return List.of();
        return selectList(new LambdaQueryWrapperX<BusinessTaskDO>()
                .select(BusinessTaskDO::getBizId, BusinessTaskDO::getDueAt, BusinessTaskDO::getId)
                .eq(BusinessTaskDO::getTaskType, TASK_TYPE_FOLLOW_UP_REMINDER)
                .eq(BusinessTaskDO::getBizType, BIZ_TYPE_LEAD)
                .in(BusinessTaskDO::getBizId, leadIds)
                .eq(BusinessTaskDO::getStatus, TASK_STATUS_PENDING)
                .orderByDesc(BusinessTaskDO::getId));
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
        return selectList(buildMyQuery(assigneeId, TASK_STATUS_PENDING, null, LocalDateTime.now())
                .orderByAsc(BusinessTaskDO::getDueAt)
                .orderByAsc(BusinessTaskDO::getId));
    }

    default List<BusinessTaskDO> selectMyPendingByBusiness(Long assigneeId, String bizType, Long bizId) {
        java.util.Objects.requireNonNull(bizType);
        return selectList(buildMyQuery(assigneeId, TASK_STATUS_PENDING, null, LocalDateTime.now())
                .eq(BusinessTaskDO::getBizType, bizType)
                .eqIfPresent(BusinessTaskDO::getBizId, bizId)
                .orderByAsc(BusinessTaskDO::getDueAt)
                .orderByAsc(BusinessTaskDO::getId));
    }

    default long selectMyPendingCount(Long assigneeId, String bucket, LocalDateTime now) {
        return selectCount(weeklyNodes(buildMyQuery(assigneeId, TASK_STATUS_PENDING, bucket, now), now));
    }

    default PageResult<BusinessTaskDO> selectMyPage(Long assigneeId, BusinessTaskPageReqVO reqVO,
                                                    LocalDateTime now) {
        java.util.Objects.requireNonNull(assigneeId);
        return selectReadPage(assigneeId, reqVO, now);
    }

    default PageResult<BusinessTaskDO> selectReadPage(Long assigneeId, BusinessTaskPageReqVO reqVO,
                                                     LocalDateTime now) {
        LambdaQueryWrapperX<BusinessTaskDO> query = buildReadQuery(
                assigneeId, reqVO.getStatus(), reqVO.getBucket(), now);
        if (TASK_STATUS_PENDING.equals(reqVO.getStatus())) weeklyNodes(query, now);
        if (TASK_STATUS_PENDING.equals(reqVO.getStatus())) {
            query.orderByAsc(BusinessTaskDO::getDueAt).orderByAsc(BusinessTaskDO::getId);
        } else {
            query.orderByDesc(BusinessTaskDO::getUpdateTime).orderByDesc(BusinessTaskDO::getId);
        }
        PageResult<BusinessTaskDO> page = selectPage(reqVO, query);
        applyCurrentFollowUpAssignees(page.getList());
        return page;
    }

    private static LambdaQueryWrapperX<BusinessTaskDO> buildMyQuery(Long assigneeId, String status,
                                                                     String bucket, LocalDateTime now) {
        java.util.Objects.requireNonNull(assigneeId);
        return buildReadQuery(assigneeId, status, bucket, now);
    }

    default long selectReadPendingCount(Long assigneeId, String bucket, LocalDateTime now) {
        return selectCount(weeklyNodes(buildReadQuery(assigneeId, TASK_STATUS_PENDING, bucket, now), now));
    }

    /** Only public pending projections collapse rows; internal task commands still see every template. */
    private static LambdaQueryWrapperX<BusinessTaskDO> weeklyNodes(LambdaQueryWrapperX<BusinessTaskDO> query, LocalDateTime now) {
        query.apply("(zsjos_business_task.biz_type <> 'media_account_diagnosis' OR (zsjos_business_task.due_at <= {0} AND NOT EXISTS ("
                + "SELECT 1 FROM zsjos_business_task sibling WHERE sibling.tenant_id=zsjos_business_task.tenant_id AND sibling.deleted=0 "
                + "AND sibling.biz_type=zsjos_business_task.biz_type AND sibling.biz_id=zsjos_business_task.biz_id "
                + "AND sibling.assignee_id=zsjos_business_task.assignee_id AND sibling.due_at=zsjos_business_task.due_at "
                + "AND sibling.status='pending' AND sibling.id < zsjos_business_task.id "
                + "AND COALESCE(JSON_UNQUOTE(JSON_EXTRACT(CASE WHEN JSON_VALID(sibling.payload)=1 THEN sibling.payload ELSE '{}' END,'$.roundKey')),'') "
                + "= COALESCE(JSON_UNQUOTE(JSON_EXTRACT(CASE WHEN JSON_VALID(zsjos_business_task.payload)=1 THEN zsjos_business_task.payload ELSE '{}' END,'$.roundKey')),''))))", now.plusDays(7));
        return query;
    }

    private static LambdaQueryWrapperX<BusinessTaskDO> buildReadQuery(Long assigneeId, String status,
                                                                     String bucket, LocalDateTime now) {
        LambdaQueryWrapperX<BusinessTaskDO> query = new LambdaQueryWrapperX<>();
        if (TASK_STATUS_PENDING.equals(status)) {
            String assignee = LeadFollowUpResponsibilitySql.taskAssignee("zsjos_business_task");
            query.apply(assignee + " IS NOT NULL");
            if (assigneeId != null) query.apply(assignee + " = {0}", assigneeId);
        } else {
            query.eqIfPresent(BusinessTaskDO::getAssigneeId, assigneeId);
        }
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
        String placeholders = java.util.stream.IntStream.range(0, assigneeIds.size())
                .mapToObj(i -> "{" + i + "}").collect(java.util.stream.Collectors.joining(","));
        List<BusinessTaskDO> tasks = selectList(new LambdaQueryWrapperX<BusinessTaskDO>()
                .apply(LeadFollowUpResponsibilitySql.taskAssignee("zsjos_business_task")
                        + " IN (" + placeholders + ")", assigneeIds.toArray()));
        applyCurrentFollowUpAssignees(tasks);
        return tasks;
    }

    default List<BusinessTaskDO> selectPendingReminderCandidates(List<String> taskTypes,
                                                                  LocalDateTime latestDueAt, int limit) {
        return selectList(new LambdaQueryWrapperX<BusinessTaskDO>()
                .in(BusinessTaskDO::getTaskType, taskTypes)
                .eq(BusinessTaskDO::getStatus, TASK_STATUS_PENDING)
                .apply(LeadFollowUpResponsibilitySql.taskAssignee("zsjos_business_task") + " IS NOT NULL")
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

    default int cancelMyPendingById(Long id, Long tenantId, Long userId, LocalDateTime at, String reason) {
        return update(null, new LambdaUpdateWrapper<BusinessTaskDO>()
                .eq(BusinessTaskDO::getId, id).eq(BusinessTaskDO::getTenantId, tenantId)
                .eq(BusinessTaskDO::getAssigneeId, userId).eq(BusinessTaskDO::getStatus, TASK_STATUS_PENDING)
                .set(BusinessTaskDO::getStatus, "cancelled")
                .set(BusinessTaskDO::getCancelledAt, at).set(BusinessTaskDO::getCancelReason, reason));
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
        boolean salesFollowUp = !taskTypes.isEmpty() && taskTypes.stream().allMatch(type ->
                List.of("lead_first_follow_up", TASK_TYPE_FOLLOW_UP_REMINDER).contains(type));
        return update(null, new LambdaUpdateWrapper<BusinessTaskDO>()
                .in(BusinessTaskDO::getTaskType, taskTypes)
                .eq(salesFollowUp, BusinessTaskDO::getBizType, BIZ_TYPE_LEAD)
                .eq(BusinessTaskDO::getBizId, bizId)
                .eq(BusinessTaskDO::getStatus, TASK_STATUS_PENDING)
                .set(BusinessTaskDO::getAssigneeId, assigneeId)
                .setSql(salesFollowUp, "version = version + 1"));
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
