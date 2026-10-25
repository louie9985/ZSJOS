package cn.iocoder.yudao.module.zsjos.dal.mysql.lead;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.LeadPublicSeaPoolCycleDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;

import java.util.List;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.publicseapool.LeadPublicSeaPoolPageReqVO;

import static cn.iocoder.yudao.module.zsjos.enums.LeadConstants.*;

@Mapper
public interface LeadPublicSeaPoolCycleMapper extends BaseMapperX<LeadPublicSeaPoolCycleDO> {
    default LeadPublicSeaPoolCycleDO selectActiveByLeadId(Long leadId) {
        return selectOne(new LambdaQueryWrapperX<LeadPublicSeaPoolCycleDO>()
                .eq(LeadPublicSeaPoolCycleDO::getLeadId, leadId)
                .in(LeadPublicSeaPoolCycleDO::getStatus, List.of(PUBLIC_SEA_POOL_WAITING_ASSIGNMENT,
                        PUBLIC_SEA_POOL_ASSIGNED, PUBLIC_SEA_POOL_DEAL_PENDING))
                .orderByDesc(LeadPublicSeaPoolCycleDO::getId).last("LIMIT 1"));
    }
    default LeadPublicSeaPoolCycleDO selectByIdempotencyKey(String key) {
        return selectOne(LeadPublicSeaPoolCycleDO::getIdempotencyKey, key);
    }
    default PageResult<LeadPublicSeaPoolCycleDO> selectPage(LeadPublicSeaPoolPageReqVO reqVO, List<Long> scopedOwnerUserIds,
                                                        Long participantUserId,
                                                        List<String> configuredStatuses, boolean matchNone,
                                                        List<Long> matchedLeadIds, Long currentUserId) {
        LambdaQueryWrapperX<LeadPublicSeaPoolCycleDO> query = new LambdaQueryWrapperX<>();
        if (participantUserId != null) {
            query.and(scope -> {
                if (scopedOwnerUserIds != null && !scopedOwnerUserIds.isEmpty()) {
                    scope.in(LeadPublicSeaPoolCycleDO::getOriginalOwnerUserId, scopedOwnerUserIds).or();
                }
                scope.eq(LeadPublicSeaPoolCycleDO::getOriginalOwnerUserId, participantUserId)
                        .or().eq(LeadPublicSeaPoolCycleDO::getCollaboratorUserId, participantUserId);
            });
        }
        // Personal filters narrow the authorized scope, including for manage-all callers.
        if (PUBLIC_SEA_POOL_RELATION_OWNED.equals(reqVO.getRelationScope())) {
            query.eq(LeadPublicSeaPoolCycleDO::getOriginalOwnerUserId, currentUserId);
        } else if (PUBLIC_SEA_POOL_RELATION_FOLLOWING.equals(reqVO.getRelationScope())) {
            query.eq(LeadPublicSeaPoolCycleDO::getCollaboratorUserId, currentUserId);
        }
        if (reqVO.getKeyword() != null && !reqVO.getKeyword().isBlank()) {
            query.exists("SELECT 1 FROM zsjos_lead l WHERE l.id = zsjos_lead_public_sea_pool_cycle.lead_id " +
                    "AND l.tenant_id = zsjos_lead_public_sea_pool_cycle.tenant_id AND l.deleted = b'0' " +
                    "AND (l.lead_no LIKE CONCAT('%',{0},'%') " +
                    "OR l.submitted_name LIKE CONCAT('%',{0},'%') " +
                    "OR l.submitted_mobile LIKE CONCAT('%',{0},'%') " +
                    "OR l.submitted_wechat_id LIKE CONCAT('%',{0},'%'))", reqVO.getKeyword().trim());
        }
        query.eqIfPresent(LeadPublicSeaPoolCycleDO::getStatus, reqVO.getStatus())
                .in(LeadPublicSeaPoolCycleDO::getStatus, List.of(PUBLIC_SEA_POOL_WAITING_ASSIGNMENT,
                        PUBLIC_SEA_POOL_ASSIGNED, PUBLIC_SEA_POOL_DEAL_PENDING));
        if (matchNone) query.apply("1 = 0");
        else query.inIfPresent(LeadPublicSeaPoolCycleDO::getStatus, configuredStatuses);
        if (matchedLeadIds != null) {
            if (matchedLeadIds.isEmpty()) query.eq(LeadPublicSeaPoolCycleDO::getLeadId, -1L);
            else query.in(LeadPublicSeaPoolCycleDO::getLeadId, matchedLeadIds);
        }
        // Public-sea entry time does not change the Lead's original submission order.
        query.getExpression().add(com.baomidou.mybatisplus.core.enums.SqlKeyword.ORDER_BY,
                () -> "(SELECT l.submitted_at FROM zsjos_lead l WHERE l.id = zsjos_lead_public_sea_pool_cycle.lead_id "
                        + "AND l.tenant_id = zsjos_lead_public_sea_pool_cycle.tenant_id AND l.deleted = b'0') DESC");
        query.orderByDesc(LeadPublicSeaPoolCycleDO::getLeadId).orderByDesc(LeadPublicSeaPoolCycleDO::getId);
        return selectPage(reqVO, query);
    }
    default long selectCountByStatus(List<Long> scopedOwnerUserIds, Long participantUserId, String status) {
        LambdaQueryWrapperX<LeadPublicSeaPoolCycleDO> query = new LambdaQueryWrapperX<>();
        if (participantUserId != null) {
            query.and(scope -> {
                if (scopedOwnerUserIds != null && !scopedOwnerUserIds.isEmpty()) {
                    scope.in(LeadPublicSeaPoolCycleDO::getOriginalOwnerUserId, scopedOwnerUserIds).or();
                }
                scope.eq(LeadPublicSeaPoolCycleDO::getOriginalOwnerUserId, participantUserId)
                        .or().eq(LeadPublicSeaPoolCycleDO::getCollaboratorUserId, participantUserId);
            });
        }
        return selectCount(query.eq(LeadPublicSeaPoolCycleDO::getStatus, status));
    }
    default int selectNextCycleNo(Long leadId) {
        LeadPublicSeaPoolCycleDO latest = selectOne(new LambdaQueryWrapperX<LeadPublicSeaPoolCycleDO>()
                .eq(LeadPublicSeaPoolCycleDO::getLeadId, leadId).orderByDesc(LeadPublicSeaPoolCycleDO::getCycleNo)
                .last("LIMIT 1"));
        return latest == null ? 1 : latest.getCycleNo() + 1;
    }
    @Select("SELECT * FROM zsjos_lead_public_sea_pool_cycle WHERE id = #{id} AND tenant_id = #{tenantId} AND deleted = b'0' FOR UPDATE")
    LeadPublicSeaPoolCycleDO selectByIdForUpdate(@Param("id") Long id, @Param("tenantId") Long tenantId);
    @Select("SELECT * FROM zsjos_lead_public_sea_pool_cycle WHERE lead_id = #{leadId} AND tenant_id = #{tenantId} " +
            "AND status IN ('waiting_assignment','assigned','deal_pending') AND deleted = b'0' ORDER BY id DESC LIMIT 1 FOR UPDATE")
    LeadPublicSeaPoolCycleDO selectActiveByLeadIdForUpdate(@Param("leadId") Long leadId,
                                                       @Param("tenantId") Long tenantId);
    default int updateWithVersion(LeadPublicSeaPoolCycleDO cycle, Integer expectedVersion) {
        return update(cycle, new LambdaUpdateWrapper<LeadPublicSeaPoolCycleDO>()
                .eq(LeadPublicSeaPoolCycleDO::getId, cycle.getId())
                .eq(LeadPublicSeaPoolCycleDO::getVersion, expectedVersion));
    }
}
