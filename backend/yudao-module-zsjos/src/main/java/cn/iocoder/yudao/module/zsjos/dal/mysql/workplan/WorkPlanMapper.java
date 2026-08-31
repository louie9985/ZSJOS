package cn.iocoder.yudao.module.zsjos.dal.mysql.workplan;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.zsjos.controller.admin.workplan.vo.WorkPlanPageReqVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.workplan.vo.WorkPlanSearchReqVO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.workplan.WorkPlanDO;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.Collection;

@Mapper
public interface WorkPlanMapper extends BaseMapperX<WorkPlanDO> {
    @Select("SELECT * FROM zsjos_work_plan WHERE id = #{id} AND tenant_id = #{tenantId} AND deleted = b'0' FOR UPDATE")
    WorkPlanDO selectByIdForUpdate(@Param("id") Long id, @Param("tenantId") Long tenantId);

    default PageResult<WorkPlanDO> selectVisiblePage(WorkPlanPageReqVO reqVO, Long userId,
                                                     Collection<Long> visiblePlanIds, boolean all) {
        return selectPage(reqVO, visibleQuery(userId, visiblePlanIds, all)
                .eqIfPresent(WorkPlanDO::getPeriodType, reqVO.getPeriodType())
                .eqIfPresent(WorkPlanDO::getStatus, reqVO.getStatus())
                .eqIfPresent(WorkPlanDO::getTemplateId, reqVO.getTemplateId())
                .eqIfPresent(WorkPlanDO::getOwnerUserId, reqVO.getOwnerUserId())
                .eqIfPresent(WorkPlanDO::getOwnerDeptId, reqVO.getOwnerDeptId())
                .geIfPresent(WorkPlanDO::getEndDate, reqVO.getStartDate())
                .leIfPresent(WorkPlanDO::getStartDate, reqVO.getEndDate())
                .orderByDesc(WorkPlanDO::getStartDate).orderByDesc(WorkPlanDO::getId));
    }

    default PageResult<WorkPlanDO> selectSearchPage(WorkPlanSearchReqVO reqVO, Long userId,
                                                    Collection<Long> visiblePlanIds, boolean all,
                                                    Collection<Long> matchedPlanIds) {
        LambdaQueryWrapperX<WorkPlanDO> query = visibleQuery(userId, visiblePlanIds, all)
                .eqIfPresent(WorkPlanDO::getPeriodType, reqVO.getPeriodType())
                .eqIfPresent(WorkPlanDO::getStatus, reqVO.getStatus())
                .eqIfPresent(WorkPlanDO::getTemplateId, reqVO.getTemplateId())
                .eqIfPresent(WorkPlanDO::getOwnerUserId, reqVO.getOwnerUserId())
                .eqIfPresent(WorkPlanDO::getOwnerDeptId, reqVO.getOwnerDeptId())
                .geIfPresent(WorkPlanDO::getEndDate, reqVO.getStartDate())
                .leIfPresent(WorkPlanDO::getStartDate, reqVO.getEndDate());
        if (matchedPlanIds != null) query.in(WorkPlanDO::getId, matchedPlanIds);
        return selectPage(reqVO, query.orderByDesc(WorkPlanDO::getStartDate).orderByDesc(WorkPlanDO::getId));
    }

    private LambdaQueryWrapperX<WorkPlanDO> visibleQuery(Long userId, Collection<Long> visiblePlanIds, boolean all) {
        LambdaQueryWrapperX<WorkPlanDO> query = new LambdaQueryWrapperX<>();
        if (!all) query.and(q -> q.eq(WorkPlanDO::getCreatorUserId, userId).or().eq(WorkPlanDO::getOwnerUserId, userId)
                .or().in(visiblePlanIds != null && !visiblePlanIds.isEmpty(), WorkPlanDO::getId, visiblePlanIds));
        return query;
    }

    default int updateDraft(WorkPlanDO plan, Integer version) {
        return update(null, new LambdaUpdateWrapper<WorkPlanDO>().eq(WorkPlanDO::getId, plan.getId())
                .eq(WorkPlanDO::getVersion, version).eq(WorkPlanDO::getStatus, "draft")
                .set(WorkPlanDO::getTitle, plan.getTitle()).set(WorkPlanDO::getPeriodType, plan.getPeriodType())
                .set(WorkPlanDO::getStartDate, plan.getStartDate()).set(WorkPlanDO::getEndDate, plan.getEndDate())
                .set(WorkPlanDO::getOwnerUserId, plan.getOwnerUserId()).set(WorkPlanDO::getOwnerDeptId, plan.getOwnerDeptId())
                .set(WorkPlanDO::getObjective, plan.getObjective()).set(WorkPlanDO::getKeyRequirements, plan.getKeyRequirements())
                .set(WorkPlanDO::getVersion, version + 1));
    }

    default int adjustActive(WorkPlanDO plan, Integer version) {
        return update(null, new LambdaUpdateWrapper<WorkPlanDO>().eq(WorkPlanDO::getId, plan.getId())
                .eq(WorkPlanDO::getVersion, version).eq(WorkPlanDO::getStatus, "active")
                .set(WorkPlanDO::getTitle, plan.getTitle()).set(WorkPlanDO::getPeriodType, plan.getPeriodType())
                .set(WorkPlanDO::getStartDate, plan.getStartDate()).set(WorkPlanDO::getEndDate, plan.getEndDate())
                .set(WorkPlanDO::getOwnerUserId, plan.getOwnerUserId()).set(WorkPlanDO::getOwnerDeptId, plan.getOwnerDeptId())
                .set(WorkPlanDO::getObjective, plan.getObjective()).set(WorkPlanDO::getKeyRequirements, plan.getKeyRequirements())
                .set(WorkPlanDO::getVersion, version + 1));
    }

    default int transition(Long id, Integer version, String fromStatus, String toStatus,
                           LocalDateTime occurredAt, String reason) {
        LambdaUpdateWrapper<WorkPlanDO> update = new LambdaUpdateWrapper<WorkPlanDO>()
                .eq(WorkPlanDO::getId, id).eq(WorkPlanDO::getVersion, version).eq(WorkPlanDO::getStatus, fromStatus)
                .set(WorkPlanDO::getStatus, toStatus).set(WorkPlanDO::getVersion, version + 1);
        if ("active".equals(toStatus)) update.set(WorkPlanDO::getPublishedAt, occurredAt);
        if ("completed".equals(toStatus)) update.set(WorkPlanDO::getCompletedAt, occurredAt);
        if ("cancelled".equals(toStatus)) update.set(WorkPlanDO::getCancelledAt, occurredAt).set(WorkPlanDO::getCancelReason, reason);
        return update(null, update);
    }
}
