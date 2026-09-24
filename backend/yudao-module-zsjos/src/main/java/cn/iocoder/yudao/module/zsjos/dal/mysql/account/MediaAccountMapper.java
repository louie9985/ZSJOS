package cn.iocoder.yudao.module.zsjos.dal.mysql.account;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.account.MediaAccountDO;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.zsjos.controller.admin.account.vo.MediaAccountPageReqVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.account.vo.MediaAccountCalendarPageReqVO;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import java.util.Collection;
import java.util.List;

@Mapper
public interface MediaAccountMapper extends BaseMapperX<MediaAccountDO> {
    default int claimDelete(Long id, Integer version, String processId, Long requester, Long reviewer, String reason) {
        return update(null, new LambdaUpdateWrapper<MediaAccountDO>().eq(MediaAccountDO::getId,id).eq(MediaAccountDO::getVersion,version)
                .and(w -> w.isNull(MediaAccountDO::getDeleteStatus).or().ne(MediaAccountDO::getDeleteStatus,"pending"))
                .set(MediaAccountDO::getDeleteProcessInstanceId,processId).set(MediaAccountDO::getDeleteRequestedByUserId,requester)
                .set(MediaAccountDO::getDeleteReviewerUserId,reviewer).set(MediaAccountDO::getDeleteReason,reason)
                .set(MediaAccountDO::getDeleteStatus,"pending").set(MediaAccountDO::getRunStatus,"delete_pending").set(MediaAccountDO::getVersion,version+1));
    }
    default MediaAccountDO selectByDeleteProcessInstanceId(String processId) { return selectOne(new LambdaQueryWrapperX<MediaAccountDO>().eq(MediaAccountDO::getDeleteProcessInstanceId,processId)); }
    default int finishDelete(Long id, Integer version, String processId, String status, String reason) {
        return update(null, new LambdaUpdateWrapper<MediaAccountDO>().eq(MediaAccountDO::getId,id).eq(MediaAccountDO::getVersion,version)
                .eq(MediaAccountDO::getDeleteProcessInstanceId,processId).set(MediaAccountDO::getDeleteStatus,status)
                .set(MediaAccountDO::getDeleteResultReason,reason).set(MediaAccountDO::getRunStatus,"approved".equals(status)?"deleted":"active").set(MediaAccountDO::getVersion,version+1));
    }
    @Select("SELECT * FROM zsjos_media_account WHERE tenant_id=#{tenantId} AND run_status='active' AND director_user_id IS NOT NULL")
    List<MediaAccountDO> selectActiveForDiagnosis(@Param("tenantId") Long tenantId);
    @Select("SELECT * FROM zsjos_media_account WHERE id=#{id} AND tenant_id=#{tenantId} "
            + "AND deleted=b'0' FOR UPDATE")
    MediaAccountDO selectByIdForUpdate(@Param("id") Long id, @Param("tenantId") Long tenantId);

    default PageResult<MediaAccountDO> selectPage(MediaAccountPageReqVO req, Collection<Long> userIds, boolean all) {
        LambdaQueryWrapperX<MediaAccountDO> q = new LambdaQueryWrapperX<>();
        q.eqIfPresent(MediaAccountDO::getSStage, req.getSStage());
        if (req.getKeyword() != null && !req.getKeyword().isBlank()) q.and(x -> x.like(MediaAccountDO::getAccountNo, req.getKeyword()).or().like(MediaAccountDO::getNickname, req.getKeyword()));
        if (!all) q.and(x -> x.in(MediaAccountDO::getOwnerOperatorUserId, userIds).or()
                .in(MediaAccountDO::getDirectorUserId, userIds));
        return selectPage(req, q.orderByDesc(MediaAccountDO::getUpdateTime).orderByDesc(MediaAccountDO::getId));
    }
    default List<Long> selectVisibleIds(Collection<Long> userIds, boolean all) {
        LambdaQueryWrapperX<MediaAccountDO> query = new LambdaQueryWrapperX<>();
        query.select(MediaAccountDO::getId);
        if (!all) query.and(x -> x.in(MediaAccountDO::getOwnerOperatorUserId, userIds).or()
                .in(MediaAccountDO::getDirectorUserId, userIds));
        return selectList(query).stream().map(MediaAccountDO::getId).toList();
    }

    default List<MediaAccountDO> selectMaterialRecommendationCandidates(String keyword, Long userId,
                                                                         Long tenantId, boolean all) {
        LambdaQueryWrapperX<MediaAccountDO> query = new LambdaQueryWrapperX<>();
        query.eq(MediaAccountDO::getTenantId, tenantId);
        if (keyword != null && !keyword.isBlank()) {
            query.and(row -> row.like(MediaAccountDO::getAccountNo, keyword.trim())
                    .or().like(MediaAccountDO::getNickname, keyword.trim()));
        }
        if (!all) {
            query.and(row -> row.nested(legacy -> legacy.isNull(MediaAccountDO::getCreateServiceRelationId)
                    .and(owner -> owner.eq(MediaAccountDO::getOwnerOperatorUserId, userId)
                            .or().eq(MediaAccountDO::getDirectorUserId, userId)))
                    .or().apply("student_person_id IS NOT NULL AND EXISTS (SELECT 1 FROM zsjos_service_relation sr "
                                    + "WHERE sr.id=zsjos_media_account.create_service_relation_id "
                                    + "AND sr.person_id=zsjos_media_account.student_person_id "
                                    + "AND sr.tenant_id=zsjos_media_account.tenant_id AND sr.tenant_id={0} AND sr.deleted=b'0' AND sr.status='active' "
                                    + "AND sr.acceptance_status='accepted' "
                                    + "AND (sr.content_director_user_id={1} OR sr.operator_user_id={1}))",
                            tenantId, userId));
        }
        return selectList(query.orderByDesc(MediaAccountDO::getUpdateTime)
                .orderByDesc(MediaAccountDO::getId).last("LIMIT 100"));
    }

    default PageResult<MediaAccountDO> selectCalendarPage(MediaAccountCalendarPageReqVO req,
                                                           Collection<Long> visibleUserIds, boolean all) {
        LambdaQueryWrapperX<MediaAccountDO> query = calendarQuery(req, visibleUserIds, all);
        query.isNotNull(MediaAccountDO::getMaintenanceStartDate)
                .isNotNull(MediaAccountDO::getMaintenanceEndDate)
                .le(MediaAccountDO::getMaintenanceStartDate, req.getRangeEnd())
                .ge(MediaAccountDO::getMaintenanceEndDate, req.getRangeStart())
                .orderByAsc(MediaAccountDO::getMaintenanceStartDate)
                .orderByAsc(MediaAccountDO::getId);
        return selectPage(req, query);
    }

    default long selectCalendarUnscheduledCount(MediaAccountCalendarPageReqVO req,
                                                 Collection<Long> visibleUserIds, boolean all) {
        LambdaQueryWrapperX<MediaAccountDO> query = calendarQuery(req, visibleUserIds, all);
        query.and(row -> row.isNull(MediaAccountDO::getMaintenanceStartDate)
                .or().isNull(MediaAccountDO::getMaintenanceEndDate));
        return selectCount(query);
    }

    default List<MediaAccountDO> selectCalendarCandidateAccounts(Collection<Long> visibleUserIds, boolean all) {
        LambdaQueryWrapperX<MediaAccountDO> query = new LambdaQueryWrapperX<>();
        query.select(MediaAccountDO::getDirectorUserId, MediaAccountDO::getOwnerOperatorUserId);
        if (!all) {
            if (visibleUserIds == null || visibleUserIds.isEmpty()) return List.of();
            query.and(row -> row.in(MediaAccountDO::getDirectorUserId, visibleUserIds)
                    .or().in(MediaAccountDO::getOwnerOperatorUserId, visibleUserIds));
        }
        return selectList(query);
    }

    private LambdaQueryWrapperX<MediaAccountDO> calendarQuery(MediaAccountCalendarPageReqVO req,
                                                               Collection<Long> visibleUserIds, boolean all) {
        LambdaQueryWrapperX<MediaAccountDO> query = new LambdaQueryWrapperX<>();
        if (!all) {
            if (visibleUserIds == null || visibleUserIds.isEmpty()) {
                query.eq(MediaAccountDO::getId, -1L);
            } else {
                query.and(row -> row.in(MediaAccountDO::getOwnerOperatorUserId, visibleUserIds)
                        .or().in(MediaAccountDO::getDirectorUserId, visibleUserIds));
            }
        }
        if (req.getKeyword() != null && !req.getKeyword().isBlank()) {
            query.and(row -> row.like(MediaAccountDO::getAccountNo, req.getKeyword().trim())
                    .or().like(MediaAccountDO::getNickname, req.getKeyword().trim()));
        }
        return query.eqIfPresent(MediaAccountDO::getCurrentStatusValue, req.getCurrentStatusValue())
                .eqIfPresent(MediaAccountDO::getSStage, req.getStageValue())
                .eqIfPresent(MediaAccountDO::getDirectorUserId, req.getDirectorUserId())
                .eqIfPresent(MediaAccountDO::getOwnerOperatorUserId, req.getOperatorUserId());
    }
    default MediaAccountDO selectByAccountNo(String accountNo) {
        return selectOne(new LambdaQueryWrapperX<MediaAccountDO>().eq(MediaAccountDO::getAccountNo, accountNo));
    }

    default MediaAccountDO selectByCreateIdempotencyKey(String idempotencyKey) {
        return selectOne(new LambdaQueryWrapperX<MediaAccountDO>()
                .eq(MediaAccountDO::getCreateIdempotencyKey, idempotencyKey));
    }

    default List<MediaAccountDO> selectByDirectorAndStudent(Long directorUserId, Long studentPersonId) {
        return selectList(new LambdaQueryWrapperX<MediaAccountDO>()
                .eq(MediaAccountDO::getDirectorUserId, directorUserId)
                .eq(MediaAccountDO::getStudentPersonId, studentPersonId)
                .orderByDesc(MediaAccountDO::getUpdateTime).orderByDesc(MediaAccountDO::getId));
    }

    default List<MediaAccountDO> selectByParticipantAndStudent(Long userId, Long studentPersonId) {
        return selectList(new LambdaQueryWrapperX<MediaAccountDO>()
                .eq(MediaAccountDO::getStudentPersonId, studentPersonId)
                .and(row -> row.eq(MediaAccountDO::getDirectorUserId, userId)
                        .or().eq(MediaAccountDO::getOwnerOperatorUserId, userId))
                .orderByDesc(MediaAccountDO::getUpdateTime).orderByDesc(MediaAccountDO::getId));
    }

    default List<MediaAccountDO> selectByStudent(Long studentPersonId) {
        return selectList(new LambdaQueryWrapperX<MediaAccountDO>()
                .eq(MediaAccountDO::getStudentPersonId, studentPersonId)
                .orderByDesc(MediaAccountDO::getUpdateTime).orderByDesc(MediaAccountDO::getId));
    }

    default List<MediaAccountDO> selectByStudents(Collection<Long> personIds) {
        if (personIds.isEmpty()) return List.of();
        return selectList(new LambdaQueryWrapperX<MediaAccountDO>()
                .in(MediaAccountDO::getStudentPersonId, personIds)
                .orderByDesc(MediaAccountDO::getUpdateTime).orderByDesc(MediaAccountDO::getId));
    }

    default int updateOwnerOperator(Long id, Long operatorUserId, Integer version) {
        return update(null, new LambdaUpdateWrapper<MediaAccountDO>()
                .eq(MediaAccountDO::getId, id)
                .eq(MediaAccountDO::getVersion, version)
                .set(MediaAccountDO::getOwnerOperatorUserId, operatorUserId)
                .set(MediaAccountDO::getVersion, version + 1));
    }

    default List<MediaAccountDO> selectRecentByParticipantAndStudent(Long userId, Long studentPersonId) {
        return selectList(new LambdaQueryWrapperX<MediaAccountDO>()
                .eq(MediaAccountDO::getStudentPersonId, studentPersonId)
                .and(row -> row.eq(MediaAccountDO::getDirectorUserId, userId)
                        .or().eq(MediaAccountDO::getOwnerOperatorUserId, userId))
                .orderByDesc(MediaAccountDO::getUpdateTime).orderByDesc(MediaAccountDO::getId)
                .last("LIMIT 100"));
    }

    default List<Long> selectParticipantStudentIds(Long userId) {
        return selectList(new LambdaQueryWrapperX<MediaAccountDO>()
                .select(MediaAccountDO::getStudentPersonId)
                .isNotNull(MediaAccountDO::getStudentPersonId)
                .and(row -> row.eq(MediaAccountDO::getDirectorUserId, userId)
                        .or().eq(MediaAccountDO::getOwnerOperatorUserId, userId)))
                .stream().map(MediaAccountDO::getStudentPersonId).distinct().toList();
    }

    default List<Long> selectParticipantStudentIds(Long userId, Collection<Long> personIds) {
        if (personIds == null || personIds.isEmpty()) return List.of();
        return selectList(new LambdaQueryWrapperX<MediaAccountDO>()
                .select(MediaAccountDO::getStudentPersonId)
                .in(MediaAccountDO::getStudentPersonId, personIds)
                .and(row -> row.eq(MediaAccountDO::getDirectorUserId, userId)
                        .or().eq(MediaAccountDO::getOwnerOperatorUserId, userId)))
                .stream().map(MediaAccountDO::getStudentPersonId).distinct().toList();
    }

    @Select("<script>SELECT DISTINCT p.id FROM zsjos_person p "
            + "WHERE p.tenant_id=#{tenantId} AND p.deleted=b'0' "
            + "<if test='keyword != null and keyword != \"\"'>AND p.name LIKE CONCAT('%',#{keyword},'%') </if>"
            + "AND EXISTS (SELECT 1 FROM zsjos_service_relation sr WHERE sr.tenant_id=p.tenant_id "
            + "AND sr.person_id=p.id AND sr.deleted=b'0' "
            + "AND ((sr.owner_user_id=#{userId} AND sr.status IN ('active','paused','completed')) "
            + "OR ((sr.content_director_user_id=#{userId} OR sr.career_planner_user_id=#{userId} OR sr.operator_user_id=#{userId}) "
            + "AND sr.status='active' AND sr.acceptance_status='accepted'))) ORDER BY p.id DESC LIMIT 100</script>")
    List<Long> selectVisibleStudentIds(@Param("userId") Long userId, @Param("keyword") String keyword,
                                       @Param("tenantId") Long tenantId);

    @Select("SELECT COUNT(1) FROM zsjos_media_account ma "
            + "JOIN zsjos_service_relation sr ON sr.person_id=ma.student_person_id "
            + "AND sr.tenant_id=ma.tenant_id AND sr.deleted=b'0' AND sr.status='active' AND sr.acceptance_status='accepted' "
            + "JOIN zsjos_order o ON o.id=sr.order_id AND o.tenant_id=sr.tenant_id AND o.deleted=b'0' "
            + "WHERE (ma.director_user_id=#{userId} OR ma.owner_operator_user_id=#{userId}) "
            + "AND o.lead_id=#{leadId} AND ma.tenant_id=#{tenantId} AND ma.deleted=b'0'")
    long countParticipantByLead(@Param("userId") Long userId, @Param("leadId") Long leadId,
                                @Param("tenantId") Long tenantId);

    default int updateProfile(MediaAccountDO account, Integer version) {
        account.setVersion(version + 1);
        return update(account, new LambdaUpdateWrapper<MediaAccountDO>()
                .eq(MediaAccountDO::getId, account.getId())
                .eq(MediaAccountDO::getVersion, version)
                // Empty selections must clear both the dictionary value and its historical label snapshot.
                .set(MediaAccountDO::getAccountTypePrimaryValue, account.getAccountTypePrimaryValue())
                .set(MediaAccountDO::getAccountTypePrimaryLabelSnapshot,
                        account.getAccountTypePrimaryLabelSnapshot())
                .set(MediaAccountDO::getAccountTypeSecondaryValue, account.getAccountTypeSecondaryValue())
                .set(MediaAccountDO::getAccountTypeSecondaryLabelSnapshot,
                        account.getAccountTypeSecondaryLabelSnapshot())
                .set(MediaAccountDO::getTrackPrimaryValue, account.getTrackPrimaryValue())
                .set(MediaAccountDO::getTrackPrimaryLabelSnapshot, account.getTrackPrimaryLabelSnapshot())
                .set(MediaAccountDO::getTrackSecondaryValue, account.getTrackSecondaryValue())
                .set(MediaAccountDO::getTrackSecondaryLabelSnapshot, account.getTrackSecondaryLabelSnapshot()));
    }

    default int updateMaintenance(MediaAccountDO account, Integer version) {
        account.setVersion(version + 1);
        return update(account, new LambdaUpdateWrapper<MediaAccountDO>()
                .eq(MediaAccountDO::getId, account.getId()).eq(MediaAccountDO::getVersion, version));
    }
    default int updateRescue(Long id, Integer version, String status) {
        return update(null, new LambdaUpdateWrapper<MediaAccountDO>().eq(MediaAccountDO::getId,id).eq(MediaAccountDO::getVersion,version).set(MediaAccountDO::getRescueStatus,status).set(MediaAccountDO::getVersion,version+1));
    }
    default int setRebindProcess(Long id, Integer version, String processId) {
        return update(null,new LambdaUpdateWrapper<MediaAccountDO>().eq(MediaAccountDO::getId,id).eq(MediaAccountDO::getVersion,version).isNull(MediaAccountDO::getRebindProcessInstanceId).set(MediaAccountDO::getRebindProcessInstanceId,processId).set(MediaAccountDO::getVersion,version+1));
    }
    default int claimRebind(Long id, Integer version, Long targetStudentId, Long requesterUserId, Long reviewerUserId) {
        return update(null, new LambdaUpdateWrapper<MediaAccountDO>().eq(MediaAccountDO::getId, id)
                .eq(MediaAccountDO::getVersion, version)
                .and(row -> row.isNull(MediaAccountDO::getRebindStatus)
                        .or().ne(MediaAccountDO::getRebindStatus, "pending"))
                .set(MediaAccountDO::getRebindProcessInstanceId, "STARTING")
                .set(MediaAccountDO::getRebindTargetStudentPersonId, targetStudentId)
                .set(MediaAccountDO::getRebindRequestedByUserId, requesterUserId)
                .set(MediaAccountDO::getRebindReviewerUserId, reviewerUserId)
                .set(MediaAccountDO::getRebindStatus, "starting")
                .set(MediaAccountDO::getRebindResultReason, null)
                .set(MediaAccountDO::getVersion, version + 1));
    }
    default int finishRebind(Long id, Integer version, String processId) {
        return update(null, new LambdaUpdateWrapper<MediaAccountDO>().eq(MediaAccountDO::getId,id)
                .eq(MediaAccountDO::getVersion,version).eq(MediaAccountDO::getRebindProcessInstanceId,"STARTING")
                .set(MediaAccountDO::getRebindProcessInstanceId,processId)
                .set(MediaAccountDO::getRebindStatus,"pending").set(MediaAccountDO::getVersion,version+1));
    }
    default MediaAccountDO selectByRebindProcessInstanceId(String processInstanceId) {
        return selectOne(new LambdaQueryWrapperX<MediaAccountDO>()
                .eq(MediaAccountDO::getRebindProcessInstanceId, processInstanceId));
    }
    default int completeRebind(Long id, Integer version, String processInstanceId, String status,
                               String reason, Long studentPersonId) {
        LambdaUpdateWrapper<MediaAccountDO> update = new LambdaUpdateWrapper<MediaAccountDO>()
                .eq(MediaAccountDO::getId, id).eq(MediaAccountDO::getVersion, version)
                .eq(MediaAccountDO::getRebindProcessInstanceId, processInstanceId)
                .eq(MediaAccountDO::getRebindStatus, "pending")
                .set(MediaAccountDO::getRebindStatus, status)
                .set(MediaAccountDO::getRebindResultReason, reason)
                .set(MediaAccountDO::getVersion, version + 1);
        if (studentPersonId != null) {
            update.set(MediaAccountDO::getStudentPersonId, studentPersonId)
                    .set(MediaAccountDO::getOwnershipType, "student");
        }
        return update(null, update);
    }
}
