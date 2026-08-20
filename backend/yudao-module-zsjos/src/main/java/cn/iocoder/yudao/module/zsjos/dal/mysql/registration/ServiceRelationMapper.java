package cn.iocoder.yudao.module.zsjos.dal.mysql.registration;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.registration.ServiceRelationDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Collection;

@Mapper
public interface ServiceRelationMapper extends BaseMapperX<ServiceRelationDO> {
    @Select("SELECT * FROM zsjos_service_relation WHERE id=#{id} AND tenant_id=#{tenantId} AND deleted=b'0' FOR UPDATE")
    ServiceRelationDO selectByIdForUpdate(@Param("id") Long id, @Param("tenantId") Long tenantId);

    default List<ServiceRelationDO> selectActiveByCollaborator(Long userId) {
        return selectList(new LambdaQueryWrapperX<ServiceRelationDO>()
                .eq(ServiceRelationDO::getStatus, "active")
                .and(query -> query.eq(ServiceRelationDO::getContentDirectorUserId, userId)
                        .or().eq(ServiceRelationDO::getCareerPlannerUserId, userId))
                .orderByDesc(ServiceRelationDO::getActivatedAt));
    }

    default int accept(Long id, Long userId, LocalDateTime now, Integer version) {
        return update(null, new LambdaUpdateWrapper<ServiceRelationDO>()
                .eq(ServiceRelationDO::getId, id).eq(ServiceRelationDO::getStatus, "active")
                .eq(ServiceRelationDO::getOwnerUserId, userId)
                .eq(ServiceRelationDO::getAcceptanceStatus, "pending")
                .eq(ServiceRelationDO::getVersion, version)
                .set(ServiceRelationDO::getAcceptanceStatus, "accepted")
                .set(ServiceRelationDO::getAcceptedByUserId, userId)
                .set(ServiceRelationDO::getAcceptedAt, now)
                .set(ServiceRelationDO::getVersion, version + 1));
    }
    default List<ServiceRelationDO> selectByOwnerUserId(Long ownerUserId) {
        return selectList(new LambdaQueryWrapperX<ServiceRelationDO>()
                .eq(ServiceRelationDO::getOwnerUserId, ownerUserId)
                .eq(ServiceRelationDO::getStatus, "active")
                .orderByDesc(ServiceRelationDO::getActivatedAt));
    }

    default List<ServiceRelationDO> selectActiveByOwnerAndPerson(Long ownerUserId, Long personId) {
        return selectList(new LambdaQueryWrapperX<ServiceRelationDO>()
                .eq(ServiceRelationDO::getOwnerUserId, ownerUserId)
                .eq(ServiceRelationDO::getPersonId, personId)
                .eq(ServiceRelationDO::getStatus, "active")
                .orderByDesc(ServiceRelationDO::getActivatedAt));
    }

    default boolean existsActiveByOwnerAndPerson(Long ownerUserId, Long personId) {
        return selectCount(new LambdaQueryWrapperX<ServiceRelationDO>()
                .eq(ServiceRelationDO::getOwnerUserId, ownerUserId)
                .eq(ServiceRelationDO::getPersonId, personId)
                .eq(ServiceRelationDO::getStatus, "active")) > 0;
    }

    @Select("SELECT COUNT(1) FROM zsjos_service_relation sr "
            + "JOIN zsjos_order o ON o.id=sr.order_id AND o.tenant_id=sr.tenant_id AND o.deleted=b'0' "
            + "WHERE sr.owner_user_id=#{ownerUserId} AND o.lead_id=#{leadId} "
            + "AND sr.status='active' AND sr.tenant_id=#{tenantId} AND sr.deleted=b'0'")
    long countActiveByOwnerAndLead(@Param("ownerUserId") Long ownerUserId,
                                   @Param("leadId") Long leadId,
                                   @Param("tenantId") Long tenantId);

    default boolean existsActiveByOwner(Long ownerUserId) {
        return selectCount(new LambdaQueryWrapperX<ServiceRelationDO>()
                .eq(ServiceRelationDO::getOwnerUserId, ownerUserId)
                .eq(ServiceRelationDO::getStatus, "active")) > 0;
    }

    default List<ServiceRelationDO> selectByRegistrationCaseIds(Collection<Long> caseIds) {
        if (caseIds == null || caseIds.isEmpty()) return List.of();
        return selectList(new LambdaQueryWrapperX<ServiceRelationDO>()
                .in(ServiceRelationDO::getRegistrationCaseId, caseIds)
                .eq(ServiceRelationDO::getStatus, "active")
                .orderByDesc(ServiceRelationDO::getActivatedAt));
    }

    default List<ServiceRelationDO> selectByRegistrationCaseIdsAndPerson(Collection<Long> caseIds, Long personId) {
        if (caseIds == null || caseIds.isEmpty()) return List.of();
        return selectList(new LambdaQueryWrapperX<ServiceRelationDO>()
                .in(ServiceRelationDO::getRegistrationCaseId, caseIds)
                .eq(ServiceRelationDO::getPersonId, personId)
                .eq(ServiceRelationDO::getStatus, "active")
                .orderByDesc(ServiceRelationDO::getActivatedAt));
    }
}
