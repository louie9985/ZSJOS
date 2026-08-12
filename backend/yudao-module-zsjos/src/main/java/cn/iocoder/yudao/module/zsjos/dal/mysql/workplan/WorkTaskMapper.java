package cn.iocoder.yudao.module.zsjos.dal.mysql.workplan;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.workplan.WorkTaskDO;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import org.apache.ibatis.annotations.Mapper;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Set;

@Mapper
public interface WorkTaskMapper extends BaseMapperX<WorkTaskDO> {
    default List<WorkTaskDO> selectListByPlanId(Long planId) {
        return selectList(new LambdaQueryWrapperX<WorkTaskDO>().eq(WorkTaskDO::getPlanId, planId)
                .orderByAsc(WorkTaskDO::getDueAt).orderByAsc(WorkTaskDO::getId));
    }

    default List<WorkTaskDO> selectChildren(Long parentTaskId) {
        return selectList(new LambdaQueryWrapperX<WorkTaskDO>().eq(WorkTaskDO::getParentTaskId, parentTaskId)
                .orderByAsc(WorkTaskDO::getDueAt).orderByAsc(WorkTaskDO::getId));
    }

    default PageResult<WorkTaskDO> selectMyPage(PageParam pageParam, String status, Long userId) {
        LambdaQueryWrapperX<WorkTaskDO> query = new LambdaQueryWrapperX<>();
        query.and(q -> q.eq(WorkTaskDO::getAssigneeUserId, userId)
                .or().eq(WorkTaskDO::getConfirmerUserId, userId)
                .or().eq(WorkTaskDO::getAssignerUserId, userId));
        query.eqIfPresent(WorkTaskDO::getStatus, status);
        query.orderByAsc(WorkTaskDO::getDueAt).orderByDesc(WorkTaskDO::getId);
        return selectPage(pageParam, query);
    }

    default List<Long> selectVisiblePlanIds(Long userId, Set<Long> deptIds, boolean all) {
        LambdaQueryWrapperX<WorkTaskDO> query = new LambdaQueryWrapperX<>();
        query.isNotNull(WorkTaskDO::getPlanId);
        if (!all) query.and(q -> q.eq(WorkTaskDO::getAssigneeUserId, userId)
                .or().eq(WorkTaskDO::getConfirmerUserId, userId)
                .or().eq(WorkTaskDO::getAssignerUserId, userId)
                .or().in(deptIds != null && !deptIds.isEmpty(), WorkTaskDO::getAssigneeDeptId, deptIds));
        return selectObjs(query.select(WorkTaskDO::getPlanId)).stream()
                .map(value -> ((Number) value).longValue()).distinct().toList();
    }

    default long countActiveByPlanId(Long planId) {
        return selectCount(new LambdaQueryWrapperX<WorkTaskDO>().eq(WorkTaskDO::getPlanId, planId)
                .in(WorkTaskDO::getStatus, List.of("draft", "pending", "awaiting_confirmation")));
    }

    default long countCompletedByPlanId(Long planId) {
        return selectCount(new LambdaQueryWrapperX<WorkTaskDO>().eq(WorkTaskDO::getPlanId, planId)
                .eq(WorkTaskDO::getStatus, "completed"));
    }

    default int transition(Long id, Integer version, Collection<String> fromStatuses, String toStatus,
                           LocalDateTime occurredAt, String cancelReason) {
        LambdaUpdateWrapper<WorkTaskDO> update = new LambdaUpdateWrapper<WorkTaskDO>()
                .eq(WorkTaskDO::getId, id).eq(WorkTaskDO::getVersion, version)
                .in(WorkTaskDO::getStatus, fromStatuses).set(WorkTaskDO::getStatus, toStatus)
                .set(WorkTaskDO::getVersion, version + 1);
        if ("awaiting_confirmation".equals(toStatus)) update.set(WorkTaskDO::getReportedAt, occurredAt);
        if ("completed".equals(toStatus)) update.set(WorkTaskDO::getCompletedAt, occurredAt);
        if ("cancelled".equals(toStatus)) update.set(WorkTaskDO::getCancelledAt, occurredAt)
                .set(WorkTaskDO::getCancelReason, cancelReason);
        if ("pending".equals(toStatus)) update.set(WorkTaskDO::getReportedAt, null)
                .set(WorkTaskDO::getCompletedAt, null);
        return update(null, update);
    }

    default int adjust(WorkTaskDO task, Integer version) {
        return update(null, new LambdaUpdateWrapper<WorkTaskDO>().eq(WorkTaskDO::getId, task.getId())
                .eq(WorkTaskDO::getVersion, version).in(WorkTaskDO::getStatus, List.of("draft", "pending"))
                .set(WorkTaskDO::getTitle, task.getTitle()).set(WorkTaskDO::getDescription, task.getDescription())
                .set(WorkTaskDO::getDeliverableRequirement, task.getDeliverableRequirement())
                .set(WorkTaskDO::getAssigneeUserId, task.getAssigneeUserId()).set(WorkTaskDO::getAssigneeDeptId, task.getAssigneeDeptId())
                .set(WorkTaskDO::getDueAt, task.getDueAt()).set(WorkTaskDO::getRemindAt, task.getRemindAt())
                .set(WorkTaskDO::getConfirmationRequired, task.getConfirmationRequired()).set(WorkTaskDO::getConfirmerUserId, task.getConfirmerUserId())
                .set(WorkTaskDO::getReminderNotifiedAt, null).set(WorkTaskDO::getOverdueNotifiedAt, null)
                .set(WorkTaskDO::getVersion, version + 1));
    }

    default List<WorkTaskDO> selectReminderCandidates(LocalDateTime now, int limit) {
        return selectList(new LambdaQueryWrapperX<WorkTaskDO>().eq(WorkTaskDO::getStatus, "pending")
                .isNull(WorkTaskDO::getReminderNotifiedAt).isNotNull(WorkTaskDO::getRemindAt)
                .le(WorkTaskDO::getRemindAt, now).orderByAsc(WorkTaskDO::getRemindAt).last("LIMIT " + limit));
    }

    default List<WorkTaskDO> selectOverdueCandidates(LocalDateTime now, int limit) {
        return selectList(new LambdaQueryWrapperX<WorkTaskDO>().eq(WorkTaskDO::getStatus, "pending")
                .isNull(WorkTaskDO::getOverdueNotifiedAt).isNotNull(WorkTaskDO::getDueAt)
                .lt(WorkTaskDO::getDueAt, now).orderByAsc(WorkTaskDO::getDueAt).last("LIMIT " + limit));
    }

    default int markReminderNotified(Long id, LocalDateTime now) {
        return update(null, new LambdaUpdateWrapper<WorkTaskDO>().eq(WorkTaskDO::getId, id)
                .isNull(WorkTaskDO::getReminderNotifiedAt).set(WorkTaskDO::getReminderNotifiedAt, now));
    }

    default int markOverdueNotified(Long id, LocalDateTime now) {
        return update(null, new LambdaUpdateWrapper<WorkTaskDO>().eq(WorkTaskDO::getId, id)
                .isNull(WorkTaskDO::getOverdueNotifiedAt).set(WorkTaskDO::getOverdueNotifiedAt, now));
    }
}
