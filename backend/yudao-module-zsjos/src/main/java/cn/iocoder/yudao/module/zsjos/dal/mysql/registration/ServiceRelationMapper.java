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
                .eq(ServiceRelationDO::getAcceptanceStatus, "accepted")
                .and(query -> query.eq(ServiceRelationDO::getContentDirectorUserId, userId)
                        .or().eq(ServiceRelationDO::getCareerPlannerUserId, userId))
                .orderByDesc(ServiceRelationDO::getActivatedAt));
    }

    default List<ServiceRelationDO> selectActiveByContentDirector(Long userId) {
        return selectList(new LambdaQueryWrapperX<ServiceRelationDO>()
                .in(ServiceRelationDO::getStatus, List.of("active", "paused", "completed"))
                .eq(ServiceRelationDO::getAcceptanceStatus, "accepted")
                .eq(ServiceRelationDO::getContentDirectorUserId, userId)
                .orderByDesc(ServiceRelationDO::getActivatedAt));
    }

    default List<ServiceRelationDO> selectActiveByContentDirectorAndPerson(Long userId, Long personId) {
        return selectList(new LambdaQueryWrapperX<ServiceRelationDO>()
                .eq(ServiceRelationDO::getPersonId, personId)
                .eq(ServiceRelationDO::getStatus, "active")
                .eq(ServiceRelationDO::getAcceptanceStatus, "accepted")
                .eq(ServiceRelationDO::getContentDirectorUserId, userId)
                .orderByDesc(ServiceRelationDO::getActivatedAt));
    }

    default List<ServiceRelationDO> selectActiveByCollaboratorAndPerson(Long userId, Long personId) {
        return selectList(new LambdaQueryWrapperX<ServiceRelationDO>()
                .eq(ServiceRelationDO::getPersonId, personId)
                .eq(ServiceRelationDO::getStatus, "active")
                .eq(ServiceRelationDO::getAcceptanceStatus, "accepted")
                .and(query -> query.eq(ServiceRelationDO::getContentDirectorUserId, userId)
                        .or().eq(ServiceRelationDO::getCareerPlannerUserId, userId))
                .orderByDesc(ServiceRelationDO::getActivatedAt));
    }

    default boolean existsActiveByCollaboratorAndPerson(Long userId, Long personId) {
        return selectCount(new LambdaQueryWrapperX<ServiceRelationDO>()
                .eq(ServiceRelationDO::getPersonId, personId)
                .eq(ServiceRelationDO::getStatus, "active")
                .eq(ServiceRelationDO::getAcceptanceStatus, "accepted")
                .and(query -> query.eq(ServiceRelationDO::getContentDirectorUserId, userId)
                        .or().eq(ServiceRelationDO::getCareerPlannerUserId, userId))) > 0;
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
                .set(ServiceRelationDO::getDeliveryStage, "first_contact")
                .set(ServiceRelationDO::getVersion, version + 1));
    }

    default int advanceDeliveryStage(Long id, Long userId, String expectedStage, String nextStage,
                                     String deliveryDataJson, Integer version) {
        return update(null, new LambdaUpdateWrapper<ServiceRelationDO>()
                .eq(ServiceRelationDO::getId, id)
                .eq(ServiceRelationDO::getStatus, "active")
                .eq(ServiceRelationDO::getOwnerUserId, userId)
                .eq(ServiceRelationDO::getAcceptanceStatus, "accepted")
                .eq(ServiceRelationDO::getDeliveryStage, expectedStage)
                .eq(ServiceRelationDO::getVersion, version)
                .set(ServiceRelationDO::getDeliveryStage, nextStage)
                .set(ServiceRelationDO::getDeliveryDataJson, deliveryDataJson)
                .set(ServiceRelationDO::getVersion, version + 1));
    }

    default int cancelActive(Long id, Integer version, LocalDateTime now, String reason) {
        return update(null, new LambdaUpdateWrapper<ServiceRelationDO>()
                .eq(ServiceRelationDO::getId, id)
                .eq(ServiceRelationDO::getStatus, "active")
                .eq(ServiceRelationDO::getVersion, version)
                .set(ServiceRelationDO::getStatus, "cancelled")
                .set(ServiceRelationDO::getTerminatedAt, now)
                .set(ServiceRelationDO::getTerminationReason, reason)
                .set(ServiceRelationDO::getVersion, version + 1));
    }
    default List<ServiceRelationDO> selectByOwnerUserId(Long ownerUserId) {
        return selectList(new LambdaQueryWrapperX<ServiceRelationDO>()
                .eq(ServiceRelationDO::getOwnerUserId, ownerUserId)
                .eq(ServiceRelationDO::getStatus, "active")
                .orderByDesc(ServiceRelationDO::getActivatedAt));
    }

    default List<ServiceRelationDO> selectByOwnerUserIdIncludingHistory(Long ownerUserId) {
        return selectList(new LambdaQueryWrapperX<ServiceRelationDO>()
                .eq(ServiceRelationDO::getOwnerUserId, ownerUserId)
                .in(ServiceRelationDO::getStatus, List.of("active", "paused", "completed"))
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

    @Select("SELECT COUNT(1) FROM zsjos_service_relation sr "
            + "JOIN zsjos_order o ON o.id=sr.order_id AND o.tenant_id=sr.tenant_id AND o.deleted=b'0' "
            + "WHERE (sr.owner_user_id=#{userId} OR sr.content_director_user_id=#{userId} "
            + "OR sr.career_planner_user_id=#{userId}) AND o.lead_id=#{leadId} "
            + "AND sr.status='active' AND sr.tenant_id=#{tenantId} AND sr.deleted=b'0'")
    long countActiveByParticipantAndLead(@Param("userId") Long userId,
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

    default List<ServiceRelationDO> selectByOwnerAndPersonIncludingHistory(Long ownerUserId, Long personId) {
        return selectList(new LambdaQueryWrapperX<ServiceRelationDO>()
                .eq(ServiceRelationDO::getOwnerUserId, ownerUserId)
                .eq(ServiceRelationDO::getPersonId, personId)
                .in(ServiceRelationDO::getStatus, List.of("active", "paused", "completed"))
                .orderByDesc(ServiceRelationDO::getActivatedAt));
    }

    default List<ServiceRelationDO> selectOwnedRepurchaseEligibleByPerson(Long ownerUserId, Long personId) {
        return selectList(new LambdaQueryWrapperX<ServiceRelationDO>()
                .eq(ServiceRelationDO::getOwnerUserId, ownerUserId)
                .eq(ServiceRelationDO::getPersonId, personId)
                .in(ServiceRelationDO::getStatus, List.of("active", "paused", "completed"))
                .eq(ServiceRelationDO::getAcceptanceStatus, "accepted")
                .orderByDesc(ServiceRelationDO::getActivatedAt));
    }

    @Select("SELECT * FROM zsjos_service_relation WHERE owner_user_id=#{ownerUserId} "
            + "AND person_id=#{personId} AND status IN ('active','paused','completed') "
            + "AND acceptance_status='accepted' AND tenant_id=#{tenantId} AND deleted=b'0' FOR UPDATE")
    List<ServiceRelationDO> selectOwnedRepurchaseEligibleByPersonForUpdate(@Param("ownerUserId") Long ownerUserId,
                                                                            @Param("personId") Long personId,
                                                                            @Param("tenantId") Long tenantId);

    default List<ServiceRelationDO> selectActiveByPersonIds(Collection<Long> personIds) {
        if (personIds == null || personIds.isEmpty()) return List.of();
        return selectList(new LambdaQueryWrapperX<ServiceRelationDO>()
                .in(ServiceRelationDO::getPersonId, personIds)
                .eq(ServiceRelationDO::getStatus, "active")
                .eq(ServiceRelationDO::getAcceptanceStatus, "accepted")
                .orderByDesc(ServiceRelationDO::getUpdateTime).orderByDesc(ServiceRelationDO::getId));
    }

    default List<ServiceRelationDO> selectAssignedByUserAndPersonIds(Long userId, Collection<Long> personIds,
                                                                     String status) {
        if (personIds == null || personIds.isEmpty()) return List.of();
        LambdaQueryWrapperX<ServiceRelationDO> query = new LambdaQueryWrapperX<>();
        query.in(ServiceRelationDO::getPersonId, personIds)
                .eqIfPresent(ServiceRelationDO::getStatus, status);
        query.and(scope -> scope.and(owner -> owner.eq(ServiceRelationDO::getOwnerUserId, userId)
                                .in(ServiceRelationDO::getStatus, List.of("active", "paused", "completed")))
                        .or(collaborator -> collaborator.eq(ServiceRelationDO::getAcceptanceStatus, "accepted")
                                .in(ServiceRelationDO::getStatus, List.of("active", "paused", "completed"))
                                .and(users -> users.eq(ServiceRelationDO::getContentDirectorUserId, userId)
                                        .or().eq(ServiceRelationDO::getCareerPlannerUserId, userId))));
        query.orderByDesc(ServiceRelationDO::getActivatedAt).orderByDesc(ServiceRelationDO::getId);
        return selectList(query);
    }
}
