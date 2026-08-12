package cn.iocoder.yudao.module.zsjos.dal.mysql.lead;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.LeadAgingPoolCycleDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;

import java.util.List;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.agingpool.LeadAgingPoolPageReqVO;

import static cn.iocoder.yudao.module.zsjos.enums.LeadConstants.*;

@Mapper
public interface LeadAgingPoolCycleMapper extends BaseMapperX<LeadAgingPoolCycleDO> {
    default LeadAgingPoolCycleDO selectActiveByLeadId(Long leadId) {
        return selectOne(new LambdaQueryWrapperX<LeadAgingPoolCycleDO>()
                .eq(LeadAgingPoolCycleDO::getLeadId, leadId)
                .in(LeadAgingPoolCycleDO::getStatus, List.of(AGING_POOL_WAITING_ASSIGNMENT,
                        AGING_POOL_ASSIGNED, AGING_POOL_DEAL_PENDING))
                .orderByDesc(LeadAgingPoolCycleDO::getId).last("LIMIT 1"));
    }
    default LeadAgingPoolCycleDO selectByIdempotencyKey(String key) {
        return selectOne(LeadAgingPoolCycleDO::getIdempotencyKey, key);
    }
    default PageResult<LeadAgingPoolCycleDO> selectPage(LeadAgingPoolPageReqVO reqVO, List<Long> scopedOwnerUserIds,
                                                        Long participantUserId,
                                                        List<String> configuredStatuses, boolean matchNone) {
        LambdaQueryWrapperX<LeadAgingPoolCycleDO> query = new LambdaQueryWrapperX<>();
        if (participantUserId != null) {
            query.and(scope -> {
                if (scopedOwnerUserIds != null && !scopedOwnerUserIds.isEmpty()) {
                    scope.in(LeadAgingPoolCycleDO::getOriginalOwnerUserId, scopedOwnerUserIds).or();
                }
                scope.eq(LeadAgingPoolCycleDO::getOriginalOwnerUserId, participantUserId)
                        .or().eq(LeadAgingPoolCycleDO::getCollaboratorUserId, participantUserId);
            });
        }
        if (reqVO.getKeyword() != null && !reqVO.getKeyword().isBlank()) {
            query.exists("SELECT 1 FROM zsjos_lead l WHERE l.id = zsjos_lead_aging_pool_cycle.lead_id " +
                    "AND l.tenant_id = zsjos_lead_aging_pool_cycle.tenant_id AND l.deleted = b'0' " +
                    "AND (l.submitted_name LIKE CONCAT('%',{0},'%') " +
                    "OR l.submitted_mobile LIKE CONCAT('%',{0},'%') " +
                    "OR l.submitted_wechat_id LIKE CONCAT('%',{0},'%'))", reqVO.getKeyword().trim());
        }
        query.eqIfPresent(LeadAgingPoolCycleDO::getStatus, reqVO.getStatus())
                .in(LeadAgingPoolCycleDO::getStatus, List.of(AGING_POOL_WAITING_ASSIGNMENT,
                        AGING_POOL_ASSIGNED, AGING_POOL_DEAL_PENDING));
        if (matchNone) query.apply("1 = 0");
        else query.inIfPresent(LeadAgingPoolCycleDO::getStatus, configuredStatuses);
        query.orderByAsc(LeadAgingPoolCycleDO::getEnteredAt).orderByAsc(LeadAgingPoolCycleDO::getId);
        return selectPage(reqVO, query);
    }
    default long selectCountByStatus(List<Long> scopedOwnerUserIds, Long participantUserId, String status) {
        LambdaQueryWrapperX<LeadAgingPoolCycleDO> query = new LambdaQueryWrapperX<>();
        if (participantUserId != null) {
            query.and(scope -> {
                if (scopedOwnerUserIds != null && !scopedOwnerUserIds.isEmpty()) {
                    scope.in(LeadAgingPoolCycleDO::getOriginalOwnerUserId, scopedOwnerUserIds).or();
                }
                scope.eq(LeadAgingPoolCycleDO::getOriginalOwnerUserId, participantUserId)
                        .or().eq(LeadAgingPoolCycleDO::getCollaboratorUserId, participantUserId);
            });
        }
        return selectCount(query.eq(LeadAgingPoolCycleDO::getStatus, status));
    }
    default int selectNextCycleNo(Long leadId) {
        LeadAgingPoolCycleDO latest = selectOne(new LambdaQueryWrapperX<LeadAgingPoolCycleDO>()
                .eq(LeadAgingPoolCycleDO::getLeadId, leadId).orderByDesc(LeadAgingPoolCycleDO::getCycleNo)
                .last("LIMIT 1"));
        return latest == null ? 1 : latest.getCycleNo() + 1;
    }
    @Select("SELECT * FROM zsjos_lead_aging_pool_cycle WHERE id = #{id} AND tenant_id = #{tenantId} AND deleted = b'0' FOR UPDATE")
    LeadAgingPoolCycleDO selectByIdForUpdate(@Param("id") Long id, @Param("tenantId") Long tenantId);
    @Select("SELECT * FROM zsjos_lead_aging_pool_cycle WHERE lead_id = #{leadId} AND tenant_id = #{tenantId} " +
            "AND status IN ('waiting_assignment','assigned','deal_pending') AND deleted = b'0' ORDER BY id DESC LIMIT 1 FOR UPDATE")
    LeadAgingPoolCycleDO selectActiveByLeadIdForUpdate(@Param("leadId") Long leadId,
                                                       @Param("tenantId") Long tenantId);
    default int updateWithVersion(LeadAgingPoolCycleDO cycle, Integer expectedVersion) {
        return update(cycle, new LambdaUpdateWrapper<LeadAgingPoolCycleDO>()
                .eq(LeadAgingPoolCycleDO::getId, cycle.getId())
                .eq(LeadAgingPoolCycleDO::getVersion, expectedVersion));
    }
}
