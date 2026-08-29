package cn.iocoder.yudao.module.zsjos.dal.mysql.lead;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.LeadDO;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.management.LeadManagementPageReqVO;
import cn.iocoder.yudao.module.zsjos.service.lead.LeadHandlingStage;
import cn.iocoder.yudao.module.zsjos.service.lead.LeadSimpleStatusQuery;

import static cn.iocoder.yudao.module.zsjos.enums.LeadConstants.ASSIGNMENT_PENDING;
import static cn.iocoder.yudao.module.zsjos.enums.LeadConstants.ASSIGNMENT_UNASSIGNED;
import static cn.iocoder.yudao.module.zsjos.enums.LeadConstants.DISPATCH_AUTO;

@Mapper
public interface LeadMapper extends BaseMapperX<LeadDO> {
    @Select("""
            SELECT
              contribution_user_id_snapshot AS contributor_user_id,
              contribution_dept_id_snapshot AS source_dept_id,
              contribution_user_name_snapshot AS contributor_name,
              contribution_dept_name_snapshot AS department_name,
              contribution_supervisor_user_id_snapshot AS supervisor_user_id,
              contribution_supervisor_name_snapshot AS supervisor_name,
              CASE WHEN provider_owner_type='partner' THEN 'part_time' ELSE 'direct' END AS contribution_type,
              provider_owner_id, provider_owner_name_snapshot AS provider_owner_name,
              SUM(CASE WHEN counted_at >= #{todayStart} THEN 1 ELSE 0 END) AS today_count,
              SUM(CASE WHEN counted_at >= #{weekStart} THEN 1 ELSE 0 END) AS week_count,
              SUM(CASE WHEN counted_at >= #{monthStart} THEN 1 ELSE 0 END) AS month_total,
              SUM(CASE WHEN counted_at >= #{monthStart} AND status IN ('valid','converted','won') THEN 1 ELSE 0 END) AS month_effective
            FROM zsjos_lead
            WHERE tenant_id=#{tenantId} AND deleted=b'0'
              AND counted_at >= LEAST(#{weekStart}, #{monthStart}) AND counted_at < #{now}
              AND provider_owner_type IN ('system_user','partner')
              AND contribution_user_id_snapshot IS NOT NULL AND contribution_dept_id_snapshot IS NOT NULL
            GROUP BY contribution_user_id_snapshot,contribution_dept_id_snapshot,
              contribution_user_name_snapshot,contribution_dept_name_snapshot,
              contribution_supervisor_user_id_snapshot,contribution_supervisor_name_snapshot,
              contribution_type,provider_owner_id,provider_owner_name_snapshot
            """)
    List<MediaScreenContributionRow> countMediaScreenContributions(@Param("tenantId") Long tenantId,
                                                                     @Param("todayStart") LocalDateTime todayStart,
                                                                     @Param("weekStart") LocalDateTime weekStart,
                                                                     @Param("monthStart") LocalDateTime monthStart,
                                                                     @Param("now") LocalDateTime now);

    @Select("""
            SELECT FLOOR(TIMESTAMPDIFF(MINUTE, #{from}, counted_at) / 10) AS bucket,
              contribution_user_id_snapshot AS contributor_user_id,
              contribution_dept_id_snapshot AS source_dept_id,
              CASE WHEN provider_owner_type='partner' THEN 'part_time' ELSE 'direct' END AS contribution_type,
              COUNT(*) AS submitted_count,
              SUM(CASE WHEN status IN ('valid','converted','won') THEN 1 ELSE 0 END) AS valid_count
            FROM zsjos_lead
            WHERE tenant_id=#{tenantId} AND deleted=b'0' AND counted_at>=#{from} AND counted_at<#{to}
              AND provider_owner_type IN ('system_user','partner')
              AND contribution_user_id_snapshot IS NOT NULL AND contribution_dept_id_snapshot IS NOT NULL
            GROUP BY bucket,contribution_user_id_snapshot,contribution_dept_id_snapshot,contribution_type
            ORDER BY bucket
            """)
    List<MediaScreenTimedContributionRow> countMediaScreenTenMinuteContributions(@Param("tenantId") Long tenantId,
                                                                                   @Param("from") LocalDateTime from,
                                                                                   @Param("to") LocalDateTime to);

    @Select("""
            SELECT DATE(counted_at) AS bucket,
              contribution_user_id_snapshot AS contributor_user_id,
              contribution_dept_id_snapshot AS source_dept_id,
              CASE WHEN provider_owner_type='partner' THEN 'part_time' ELSE 'direct' END AS contribution_type,
              COUNT(*) AS submitted_count,
              SUM(CASE WHEN status IN ('valid','converted','won') THEN 1 ELSE 0 END) AS valid_count
            FROM zsjos_lead
            WHERE tenant_id=#{tenantId} AND deleted=b'0' AND counted_at>=#{from} AND counted_at<#{to}
              AND provider_owner_type IN ('system_user','partner')
              AND contribution_user_id_snapshot IS NOT NULL AND contribution_dept_id_snapshot IS NOT NULL
            GROUP BY bucket,contribution_user_id_snapshot,contribution_dept_id_snapshot,contribution_type
            ORDER BY bucket
            """)
    List<MediaScreenTimedContributionRow> countMediaScreenDailyContributions(@Param("tenantId") Long tenantId,
                                                                              @Param("from") LocalDateTime from,
                                                                              @Param("to") LocalDateTime to);

    @Select("SELECT COUNT(*) AS total FROM zsjos_lead WHERE tenant_id=#{tenantId} AND deleted=b'0'")
    long countForMediaScreen(@Param("tenantId") Long tenantId);

    @Select("SELECT source_dept_id AS bucket, COUNT(*) AS total FROM zsjos_lead WHERE tenant_id=#{tenantId} AND deleted=b'0' GROUP BY source_dept_id ORDER BY total DESC")
    List<Map<String, Object>> countMediaScreenDepartments(@Param("tenantId") Long tenantId);

    @Select("SELECT owner_user_id AS bucket, COUNT(*) AS total FROM zsjos_lead WHERE tenant_id=#{tenantId} AND deleted=b'0' AND owner_user_id IS NOT NULL GROUP BY owner_user_id ORDER BY total DESC")
    List<Map<String, Object>> countMediaScreenMembers(@Param("tenantId") Long tenantId);

    @Select("SELECT DATE(submitted_at) AS bucket, COUNT(*) AS total FROM zsjos_lead WHERE tenant_id=#{tenantId} AND deleted=b'0' AND submitted_at >= #{from} AND submitted_at < #{to} GROUP BY DATE(submitted_at) ORDER BY bucket")
    List<Map<String, Object>> countMediaScreenTrend(@Param("tenantId") Long tenantId, @Param("from") LocalDateTime from, @Param("to") LocalDateTime to);
    default LeadDO selectByPersonId(Long personId) {
        return selectOne(new LambdaQueryWrapperX<LeadDO>().eq(LeadDO::getPersonId, personId)
                .orderByDesc(LeadDO::getLastActivityAt).orderByDesc(LeadDO::getId).last("LIMIT 1"));
    }
    default void updateSourceDeptByPartnerId(Long partnerId, Long deptId) {
        update(new LeadDO().setSourceDeptId(deptId), new LambdaUpdateWrapper<LeadDO>()
                .eq(LeadDO::getPartnerId, partnerId));
    }
    @Select("""
            SELECT l.* FROM zsjos_lead l
            JOIN zsjos_opportunity o ON o.lead_id=l.id AND o.type='initial_conversion'
              AND o.status IN ('open','following') AND o.deleted=b'0' AND o.tenant_id=l.tenant_id
            WHERE l.tenant_id=#{tenantId} AND l.deleted=b'0' AND l.assignment_status='owned'
              AND l.status = 'valid' AND l.owner_user_id IS NOT NULL
              AND COALESCE((SELECT MAX(ofu.occurred_at) FROM zsjos_opportunity_follow_up_record ofu
                    WHERE ofu.opportunity_id=o.id AND ofu.tenant_id=l.tenant_id AND ofu.deleted=b'0'),
                    o.create_time) <= #{cutoff}
              AND NOT EXISTS (SELECT 1 FROM zsjos_lead_aging_pool_cycle c WHERE c.lead_id=l.id
                AND c.tenant_id=l.tenant_id AND c.deleted=b'0'
                AND c.status IN ('waiting_assignment','assigned','deal_pending'))
              AND NOT EXISTS (SELECT 1 FROM zsjos_order so WHERE so.lead_id=l.id
                AND so.tenant_id=l.tenant_id AND so.deleted=b'0' AND so.status='pending_approval')
            ORDER BY o.create_time ASC,l.id ASC LIMIT 200
            """)
    List<LeadDO> selectAgingPoolCandidates(@Param("tenantId") Long tenantId,
                                           @Param("cutoff") LocalDateTime cutoff);
    @Select("""
            SELECT l.* FROM zsjos_lead l
            JOIN zsjos_opportunity o ON o.lead_id=l.id AND o.type='initial_conversion'
              AND o.status IN ('open','following') AND o.deleted=b'0' AND o.tenant_id=l.tenant_id
            WHERE l.tenant_id=#{tenantId} AND l.deleted=b'0' AND l.assignment_status='owned'
              AND l.status = 'valid' AND l.owner_user_id IS NOT NULL
              AND COALESCE((SELECT MAX(ofu.occurred_at) FROM zsjos_opportunity_follow_up_record ofu
                    WHERE ofu.opportunity_id=o.id AND ofu.tenant_id=l.tenant_id AND ofu.deleted=b'0'),
                    o.create_time) <= #{latestStart}
              AND NOT EXISTS (SELECT 1 FROM zsjos_lead_aging_pool_cycle c WHERE c.lead_id=l.id
                AND c.tenant_id=l.tenant_id AND c.deleted=b'0'
                AND c.status IN ('waiting_assignment','assigned','deal_pending'))
              AND NOT EXISTS (SELECT 1 FROM zsjos_order so WHERE so.lead_id=l.id
                AND so.tenant_id=l.tenant_id AND so.deleted=b'0' AND so.status='pending_approval')
            ORDER BY o.create_time ASC,l.id ASC LIMIT 500
            """)
    List<LeadDO> selectAgingPoolReminderCandidates(@Param("tenantId") Long tenantId,
                                                   @Param("latestStart") LocalDateTime latestStart);

    default List<Long> selectIdsByContactKeyword(String keyword) {
        LambdaQueryWrapperX<LeadDO> query = new LambdaQueryWrapperX<>();
        query.select(LeadDO::getId);
        applyLeadKeyword(query, keyword);
        return selectList(query)
                .stream().map(LeadDO::getId).toList();
    }

    @Select("SELECT * FROM zsjos_lead WHERE id = #{id} AND tenant_id = #{tenantId} AND deleted = b'0' FOR UPDATE")
    LeadDO selectByIdForUpdate(@Param("id") Long id, @Param("tenantId") Long tenantId);

    default List<LeadDO> selectByOwnerUserIds(List<Long> ownerUserIds) {
        if (ownerUserIds == null || ownerUserIds.isEmpty()) return List.of();
        return selectList(new LambdaQueryWrapperX<LeadDO>().in(LeadDO::getOwnerUserId, ownerUserIds));
    }

    default PageResult<LeadDO> selectManagementPage(LeadManagementPageReqVO reqVO, Long visibleUserId,
                                                     List<String> inboxStatuses,
                                                     List<String> inboxAssignmentStatuses,
                                                     boolean inboxMatchNone) {
        return selectManagementPage(reqVO, visibleUserId, List.of(), inboxStatuses,
                inboxAssignmentStatuses, inboxMatchNone);
    }
    default PageResult<LeadDO> selectManagementPage(LeadManagementPageReqVO reqVO, Long visibleUserId,
                                                     List<Long> managedOwnerUserIds,
                                                     List<String> inboxStatuses,
                                                     List<String> inboxAssignmentStatuses,
                                                     boolean inboxMatchNone) {
        return selectManagementPage(reqVO, visibleUserId, managedOwnerUserIds, inboxStatuses,
                inboxAssignmentStatuses, inboxMatchNone, null);
    }
    default PageResult<LeadDO> selectManagementPage(LeadManagementPageReqVO reqVO, Long visibleUserId,
                                                     List<Long> managedOwnerUserIds,
                                                     List<String> inboxStatuses,
                                                     List<String> inboxAssignmentStatuses,
                                                     boolean inboxMatchNone, List<Long> matchedLeadIds) {
        return selectManagementPage(reqVO, visibleUserId, managedOwnerUserIds, inboxStatuses,
                inboxAssignmentStatuses, List.of(), inboxMatchNone, matchedLeadIds);
    }
    default PageResult<LeadDO> selectManagementPage(LeadManagementPageReqVO reqVO, Long visibleUserId,
                                                     List<Long> managedOwnerUserIds,
                                                     List<String> inboxStatuses,
                                                     List<String> inboxAssignmentStatuses,
                                                     List<String> inboxHandlingStages,
                                                     boolean inboxMatchNone, List<Long> matchedLeadIds) {
        LambdaQueryWrapperX<LeadDO> query = new LambdaQueryWrapperX<LeadDO>()
                .eqIfPresent(LeadDO::getStatus, reqVO.getStatus())
                .eqIfPresent(LeadDO::getAssignmentStatus, reqVO.getAssignmentStatus())
                .eqIfPresent(LeadDO::getSourceChannelId, reqVO.getSourceChannel())
                .eqIfPresent(LeadDO::getLeadCategory, reqVO.getLeadCategory())
                .eqIfPresent(LeadDO::getSourceUserId, reqVO.getSourceUserId())
                .eqIfPresent(LeadDO::getOwnerUserId, reqVO.getOwnerUserId())
                .betweenIfPresent(LeadDO::getSubmittedAt, reqVO.getSubmittedAt());
        if (inboxMatchNone) {
            query.eq(LeadDO::getId, -1L);
        } else {
            if (inboxStatuses != null && !inboxStatuses.isEmpty()) {
                query.in(LeadDO::getStatus, inboxStatuses);
            }
            if (inboxAssignmentStatuses != null && !inboxAssignmentStatuses.isEmpty()) {
                query.in(LeadDO::getAssignmentStatus, inboxAssignmentStatuses);
            }
            if (inboxHandlingStages != null && !inboxHandlingStages.isEmpty()) {
                query.eq(LeadDO::getStatus, "submitted").eq(LeadDO::getAssignmentStatus, "owned");
                if (inboxHandlingStages.size() == 1
                        && inboxHandlingStages.contains(LeadHandlingStage.FIRST_FOLLOW_PENDING)) {
                    query.isNull(LeadDO::getQualificationDeadlineAt);
                } else if (inboxHandlingStages.size() == 1
                        && inboxHandlingStages.contains(LeadHandlingStage.QUALIFICATION_PENDING)) {
                    query.isNotNull(LeadDO::getQualificationDeadlineAt);
                }
            }
        }
        if (matchedLeadIds != null) {
            if (matchedLeadIds.isEmpty()) query.eq(LeadDO::getId, -1L);
            else query.in(LeadDO::getId, matchedLeadIds);
        }
        if (reqVO.getKeyword() != null && !reqVO.getKeyword().isBlank()) {
            applyLeadKeyword(query, reqVO.getKeyword());
        }
        if (visibleUserId != null) {
            if ("submitter".equals(reqVO.getAudience())) {
                query.eq(LeadDO::getSourceUserId, visibleUserId);
            } else if ("owner".equals(reqVO.getAudience())) {
                query.eq(LeadDO::getOwnerUserId, visibleUserId);
            } else if (managedOwnerUserIds != null && !managedOwnerUserIds.isEmpty()) {
                query.and(wrapper -> wrapper.eq(LeadDO::getSourceUserId, visibleUserId)
                        .or().eq(LeadDO::getOwnerUserId, visibleUserId)
                        .or().in(LeadDO::getOwnerUserId, managedOwnerUserIds));
            } else {
                query.and(wrapper -> wrapper.eq(LeadDO::getSourceUserId, visibleUserId)
                        .or().eq(LeadDO::getOwnerUserId, visibleUserId));
            }
        }
        if (reqVO.getCursorActivityAt() != null && reqVO.getCursorId() != null) {
            query.and(wrapper -> wrapper.lt(LeadDO::getLastActivityAt, reqVO.getCursorActivityAt())
                    .or(nested -> nested.eq(LeadDO::getLastActivityAt, reqVO.getCursorActivityAt())
                            .lt(LeadDO::getId, reqVO.getCursorId())));
        }
        query.orderByDesc(LeadDO::getLastActivityAt).orderByDesc(LeadDO::getId);
        return selectPage(reqVO, query);
    }

    default PageResult<LeadDO> selectManagementPageByScope(LeadManagementPageReqVO reqVO,
                                                            List<Long> visibleSourceUserIds,
                                                            List<Long> visibleOwnerUserIds,
                                                            boolean queryAll,
                                                            List<String> inboxStatuses,
                                                            List<String> inboxAssignmentStatuses,
                                                            List<String> inboxHandlingStages,
                                                            boolean inboxMatchNone,
                                                            List<Long> matchedLeadIds) {
        LambdaQueryWrapperX<LeadDO> query = new LambdaQueryWrapperX<LeadDO>()
                .eqIfPresent(LeadDO::getStatus, reqVO.getStatus())
                .eqIfPresent(LeadDO::getAssignmentStatus, reqVO.getAssignmentStatus())
                .eqIfPresent(LeadDO::getSourceChannelId, reqVO.getSourceChannel())
                .eqIfPresent(LeadDO::getLeadCategory, reqVO.getLeadCategory())
                .eqIfPresent(LeadDO::getSourceUserId, reqVO.getSourceUserId())
                .eqIfPresent(LeadDO::getProviderOwnerType, reqVO.getProviderOwnerType())
                .eqIfPresent(LeadDO::getProviderOwnerId, reqVO.getProviderOwnerId())
                .eqIfPresent(LeadDO::getOwnerUserId, reqVO.getOwnerUserId())
                .betweenIfPresent(LeadDO::getSubmittedAt, reqVO.getSubmittedAt());
        if (inboxMatchNone) {
            query.eq(LeadDO::getId, -1L);
        } else {
            if (inboxStatuses != null && !inboxStatuses.isEmpty()) query.in(LeadDO::getStatus, inboxStatuses);
            if (inboxAssignmentStatuses != null && !inboxAssignmentStatuses.isEmpty()) {
                query.in(LeadDO::getAssignmentStatus, inboxAssignmentStatuses);
            }
            if (inboxHandlingStages != null && !inboxHandlingStages.isEmpty()) {
                query.eq(LeadDO::getStatus, "submitted").eq(LeadDO::getAssignmentStatus, "owned");
                if (inboxHandlingStages.size() == 1
                        && inboxHandlingStages.contains(LeadHandlingStage.FIRST_FOLLOW_PENDING)) {
                    query.isNull(LeadDO::getQualificationDeadlineAt);
                } else if (inboxHandlingStages.size() == 1
                        && inboxHandlingStages.contains(LeadHandlingStage.QUALIFICATION_PENDING)) {
                    query.isNotNull(LeadDO::getQualificationDeadlineAt);
                }
            }
        }
        applySimpleStatus(query, LeadSimpleStatusQuery.resolve(reqVO.getSimpleStatus()));
        if (matchedLeadIds != null) {
            if (matchedLeadIds.isEmpty()) query.eq(LeadDO::getId, -1L);
            else query.in(LeadDO::getId, matchedLeadIds);
        }
        if (reqVO.getKeyword() != null && !reqVO.getKeyword().isBlank()) applyLeadKeyword(query, reqVO.getKeyword());
        if (!queryAll) {
            boolean hasSourceScope = visibleSourceUserIds != null && !visibleSourceUserIds.isEmpty();
            boolean hasOwnerScope = visibleOwnerUserIds != null && !visibleOwnerUserIds.isEmpty();
            if (!hasSourceScope && !hasOwnerScope) {
                query.eq(LeadDO::getId, -1L);
            } else {
                query.and(wrapper -> {
                    if (hasSourceScope) wrapper.eq(LeadDO::getProviderOwnerType, "system_user")
                            .in(LeadDO::getProviderOwnerId, visibleSourceUserIds);
                    if (hasOwnerScope) {
                        if (hasSourceScope) wrapper.or();
                        wrapper.in(LeadDO::getOwnerUserId, visibleOwnerUserIds)
                                .or().in(LeadDO::getRecycleSourceOwnerUserId, visibleOwnerUserIds);
                    }
                });
            }
        }
        if (reqVO.getCursorActivityAt() != null && reqVO.getCursorId() != null) {
            query.and(wrapper -> wrapper.lt(LeadDO::getLastActivityAt, reqVO.getCursorActivityAt())
                    .or(nested -> nested.eq(LeadDO::getLastActivityAt, reqVO.getCursorActivityAt())
                            .lt(LeadDO::getId, reqVO.getCursorId())));
        }
        query.orderByDesc(LeadDO::getLastActivityAt).orderByDesc(LeadDO::getId);
        return selectPage(reqVO, query);
    }

    private static void applySimpleStatus(LambdaQueryWrapperX<LeadDO> query, LeadSimpleStatusQuery filter) {
        if (!filter.leadStatuses().isEmpty()) query.in(LeadDO::getStatus, filter.leadStatuses());
        if (!filter.assignmentStatuses().isEmpty()) {
            query.in(LeadDO::getAssignmentStatus, filter.assignmentStatuses());
        }
        if (filter.handlingStages().contains(LeadHandlingStage.FIRST_FOLLOW_PENDING)) {
            query.isNull(LeadDO::getQualificationDeadlineAt);
        } else if (filter.handlingStages().contains(LeadHandlingStage.QUALIFICATION_PENDING)) {
            query.isNotNull(LeadDO::getQualificationDeadlineAt);
        }
        applyOpportunityStatus(query, filter.requiredOpportunityStatuses(), false);
        applyOpportunityStatus(query, filter.excludedOpportunityStatuses(), true);
    }

    private static void applyOpportunityStatus(LambdaQueryWrapperX<LeadDO> query, java.util.Set<String> statuses,
                                               boolean excluded) {
        if (statuses.isEmpty()) return;
        String placeholders = java.util.stream.IntStream.range(0, statuses.size())
                .mapToObj(index -> "{" + index + "}").collect(java.util.stream.Collectors.joining(","));
        String sql = "SELECT 1 FROM zsjos_opportunity o WHERE o.lead_id = zsjos_lead.id "
                + "AND o.tenant_id = zsjos_lead.tenant_id AND o.type = 'initial_conversion' "
                + "AND o.deleted = b'0' AND o.status IN (" + placeholders + ")";
        Object[] parameters = statuses.toArray();
        if (excluded) query.notExists(sql, parameters);
        else query.exists(sql, parameters);
    }

    default PageResult<LeadDO> selectPartnerPage(LeadManagementPageReqVO reqVO, Long partnerId) {
        LambdaQueryWrapperX<LeadDO> query = new LambdaQueryWrapperX<LeadDO>()
                .eq(LeadDO::getPartnerId, partnerId)
                .eqIfPresent(LeadDO::getStatus, reqVO.getStatus())
                .eqIfPresent(LeadDO::getAssignmentStatus, reqVO.getAssignmentStatus())
                .eqIfPresent(LeadDO::getSourceChannelId, reqVO.getSourceChannel())
                .eqIfPresent(LeadDO::getLeadCategory, reqVO.getLeadCategory())
                .betweenIfPresent(LeadDO::getSubmittedAt, reqVO.getSubmittedAt());
        if (reqVO.getKeyword() != null && !reqVO.getKeyword().isBlank()) applyLeadKeyword(query, reqVO.getKeyword());
        applyPartnerView(query, reqVO.getView());
        if (reqVO.getView() == null || reqVO.getView().isBlank() || "all".equals(reqVO.getView())) {
            applySimpleStatus(query, LeadSimpleStatusQuery.resolve(reqVO.getSimpleStatus()));
        }
        query.orderByDesc(LeadDO::getLastActivityAt).orderByDesc(LeadDO::getId);
        return selectPage(reqVO, query);
    }

    private static void applyPartnerView(LambdaQueryWrapperX<LeadDO> query, String view) {
        if (view == null || view.isBlank() || "all".equals(view)) return;
        switch (view) {
            case "invalid" -> query.eq(LeadDO::getStatus, "invalid");
            case "follow_up_pending" -> query.apply("(" +
                    "(status='submitted' AND assignment_status='owned')" +
                    " OR (status='valid' AND EXISTS (" +
                    "SELECT 1 FROM zsjos_opportunity o WHERE o.lead_id=zsjos_lead.id " +
                    "AND o.tenant_id=zsjos_lead.tenant_id AND o.type='initial_conversion' " +
                    "AND o.deleted=b'0' AND o.status IN ('open','following')))" +
                    ")");
            case "unreachable" -> query.apply(latestFollowUpResultSql("unreachable"));
            default -> throw new IllegalArgumentException("Unsupported partner lead view: " + view);
        }
    }

    private static String latestFollowUpResultSql(String result) {
        return "(EXISTS (SELECT 1 FROM zsjos_lead_follow_up_record current_lead " +
                "WHERE current_lead.lead_id=zsjos_lead.id AND current_lead.tenant_id=zsjos_lead.tenant_id " +
                "AND current_lead.deleted=b'0' AND current_lead.result_value='" + result + "' " +
                "AND NOT EXISTS (SELECT 1 FROM zsjos_lead_follow_up_record newer_lead " +
                "WHERE newer_lead.lead_id=zsjos_lead.id AND newer_lead.tenant_id=zsjos_lead.tenant_id " +
                "AND newer_lead.deleted=b'0' AND (newer_lead.occurred_at>current_lead.occurred_at " +
                "OR (newer_lead.occurred_at=current_lead.occurred_at AND newer_lead.id>current_lead.id))) " +
                "AND NOT EXISTS (SELECT 1 FROM zsjos_opportunity_follow_up_record newer_opp " +
                "WHERE newer_opp.lead_id=zsjos_lead.id AND newer_opp.tenant_id=zsjos_lead.tenant_id " +
                "AND newer_opp.deleted=b'0' AND (newer_opp.occurred_at>current_lead.occurred_at " +
                "OR (newer_opp.occurred_at=current_lead.occurred_at AND newer_opp.id>current_lead.id))) ) " +
                "OR EXISTS (SELECT 1 FROM zsjos_opportunity_follow_up_record current_opp " +
                "WHERE current_opp.lead_id=zsjos_lead.id AND current_opp.tenant_id=zsjos_lead.tenant_id " +
                "AND current_opp.deleted=b'0' AND current_opp.result_value='" + result + "' " +
                "AND NOT EXISTS (SELECT 1 FROM zsjos_lead_follow_up_record newer_lead " +
                "WHERE newer_lead.lead_id=zsjos_lead.id AND newer_lead.tenant_id=zsjos_lead.tenant_id " +
                "AND newer_lead.deleted=b'0' AND (newer_lead.occurred_at>current_opp.occurred_at " +
                "OR (newer_lead.occurred_at=current_opp.occurred_at AND newer_lead.id>current_opp.id))) " +
                "AND NOT EXISTS (SELECT 1 FROM zsjos_opportunity_follow_up_record newer_opp " +
                "WHERE newer_opp.lead_id=zsjos_lead.id AND newer_opp.tenant_id=zsjos_lead.tenant_id " +
                "AND newer_opp.deleted=b'0' AND (newer_opp.occurred_at>current_opp.occurred_at " +
                "OR (newer_opp.occurred_at=current_opp.occurred_at AND newer_opp.id>current_opp.id))))");
    }

    @Select("SELECT COUNT(*) FROM zsjos_lead WHERE partner_id=#{partnerId} AND deleted=b'0' " +
            "AND ((status='submitted' AND assignment_status='owned') " +
            "OR (status='valid' AND EXISTS (SELECT 1 FROM zsjos_opportunity o WHERE o.lead_id=zsjos_lead.id " +
            "AND o.tenant_id=zsjos_lead.tenant_id AND o.type='initial_conversion' AND o.deleted=b'0' " +
            "AND o.status IN ('open','following'))))")
    long countPartnerFollowUpPending(@Param("partnerId") Long partnerId);

    @Select("SELECT COUNT(*) FROM zsjos_lead WHERE partner_id=#{partnerId} AND deleted=b'0' " +
            "AND " +
            "(EXISTS (SELECT 1 FROM zsjos_lead_follow_up_record current_lead WHERE current_lead.lead_id=zsjos_lead.id " +
            "AND current_lead.tenant_id=zsjos_lead.tenant_id AND current_lead.deleted=b'0' AND current_lead.result_value='unreachable' " +
            "AND NOT EXISTS (SELECT 1 FROM zsjos_lead_follow_up_record newer_lead WHERE newer_lead.lead_id=zsjos_lead.id " +
            "AND newer_lead.tenant_id=zsjos_lead.tenant_id AND newer_lead.deleted=b'0' AND (newer_lead.occurred_at>current_lead.occurred_at " +
            "OR (newer_lead.occurred_at=current_lead.occurred_at AND newer_lead.id>current_lead.id))) " +
            "AND NOT EXISTS (SELECT 1 FROM zsjos_opportunity_follow_up_record newer_opp WHERE newer_opp.lead_id=zsjos_lead.id " +
            "AND newer_opp.tenant_id=zsjos_lead.tenant_id AND newer_opp.deleted=b'0' AND (newer_opp.occurred_at>current_lead.occurred_at " +
            "OR (newer_opp.occurred_at=current_lead.occurred_at AND newer_opp.id>current_lead.id)))) " +
            "OR EXISTS (SELECT 1 FROM zsjos_opportunity_follow_up_record current_opp WHERE current_opp.lead_id=zsjos_lead.id " +
            "AND current_opp.tenant_id=zsjos_lead.tenant_id AND current_opp.deleted=b'0' AND current_opp.result_value='unreachable' " +
            "AND NOT EXISTS (SELECT 1 FROM zsjos_lead_follow_up_record newer_lead WHERE newer_lead.lead_id=zsjos_lead.id " +
            "AND newer_lead.tenant_id=zsjos_lead.tenant_id AND newer_lead.deleted=b'0' AND (newer_lead.occurred_at>current_opp.occurred_at " +
            "OR (newer_lead.occurred_at=current_opp.occurred_at AND newer_lead.id>current_opp.id))) " +
            "AND NOT EXISTS (SELECT 1 FROM zsjos_opportunity_follow_up_record newer_opp WHERE newer_opp.lead_id=zsjos_lead.id " +
            "AND newer_opp.tenant_id=zsjos_lead.tenant_id AND newer_opp.deleted=b'0' AND (newer_opp.occurred_at>current_opp.occurred_at " +
            "OR (newer_opp.occurred_at=current_opp.occurred_at AND newer_opp.id>current_opp.id))))")
    long countPartnerUnreachable(@Param("partnerId") Long partnerId);

    @Select("SELECT COUNT(*) FROM zsjos_lead WHERE partner_id=#{partnerId} " +
            "AND deleted=b'0' AND status='invalid'")
    long countPartnerInvalid(@Param("partnerId") Long partnerId);

    default void touchActivity(Long leadId, java.time.LocalDateTime occurredAt) {
        update(new LeadDO().setLastActivityAt(occurredAt),
                new LambdaQueryWrapperX<LeadDO>().eq(LeadDO::getId, leadId));
    }

    default Map<String, Long> selectManagementStatusCounts(Long visibleUserId, List<Long> managedOwnerUserIds) {
        QueryWrapper<LeadDO> query = new QueryWrapper<LeadDO>()
                .select("status", "COUNT(*) AS total")
                .groupBy("status");
        if (visibleUserId != null) {
            query.and(wrapper -> {
                wrapper.eq("source_user_id", visibleUserId).or().eq("owner_user_id", visibleUserId);
                if (managedOwnerUserIds != null && !managedOwnerUserIds.isEmpty()) {
                    wrapper.or().in("owner_user_id", managedOwnerUserIds);
                }
            });
        }
        Map<String, Long> result = new LinkedHashMap<>();
        for (Map<String, Object> row : selectMaps(query)) {
            Object status = row.get("status");
            Object total = row.get("total");
            if (status != null && total instanceof Number number) {
                result.put(status.toString(), number.longValue());
            }
        }
        return result;
    }

    default Map<String, Long> selectManagementStatusCountsByScope(List<Long> visibleSourceUserIds,
                                                                   List<Long> visibleOwnerUserIds,
                                                                   boolean queryAll) {
        QueryWrapper<LeadDO> query = new QueryWrapper<LeadDO>()
                .select("status", "COUNT(*) AS total")
                .groupBy("status");
        if (!queryAll) {
            boolean hasSourceScope = visibleSourceUserIds != null && !visibleSourceUserIds.isEmpty();
            boolean hasOwnerScope = visibleOwnerUserIds != null && !visibleOwnerUserIds.isEmpty();
            if (!hasSourceScope && !hasOwnerScope) {
                query.eq("id", -1L);
            } else {
                query.and(wrapper -> {
                    if (hasSourceScope) wrapper.eq("provider_owner_type", "system_user")
                            .in("provider_owner_id", visibleSourceUserIds);
                    if (hasOwnerScope) {
                        if (hasSourceScope) wrapper.or();
                        wrapper.in("owner_user_id", visibleOwnerUserIds);
                    }
                });
            }
        }
        Map<String, Long> result = new LinkedHashMap<>();
        for (Map<String, Object> row : selectMaps(query)) {
            Object status = row.get("status");
            Object total = row.get("total");
            if (status != null && total instanceof Number number) result.put(status.toString(), number.longValue());
        }
        return result;
    }

    default List<Map<String, Object>> selectManagementInboxStateCounts(Long visibleUserId, String audience) {
        QueryWrapper<LeadDO> query = new QueryWrapper<LeadDO>()
                .select("status", "assignment_status",
                        "CASE WHEN status='submitted' AND assignment_status='owned' "
                                + "THEN CASE WHEN qualification_deadline_at IS NULL "
                                + "THEN 'first_follow_pending' ELSE 'qualification_pending' END ELSE NULL END AS handling_stage",
                        "COUNT(*) AS total")
                .groupBy("status", "assignment_status", "handling_stage");
        if (visibleUserId != null) {
            if ("submitter".equals(audience)) {
                query.eq("provider_owner_type", "system_user").eq("provider_owner_id", visibleUserId);
            } else if ("owner".equals(audience)) {
                query.eq("owner_user_id", visibleUserId);
            } else {
                query.and(wrapper -> wrapper.eq("provider_owner_type", "system_user")
                        .eq("provider_owner_id", visibleUserId)
                        .or().eq("owner_user_id", visibleUserId));
            }
        }
        return selectMaps(query);
    }

    default LeadDO selectByIdempotencyKey(String key) {
        return selectOne(new LambdaQueryWrapperX<LeadDO>().eq(LeadDO::getSubmissionIdempotencyKey, key));
    }
    default LeadDO selectLatestByPersonId(Long personId) {
        return selectOne(new LambdaQueryWrapperX<LeadDO>().eq(LeadDO::getPersonId, personId)
                .orderByDesc(LeadDO::getSubmittedAt).last("LIMIT 1"));
    }
    default List<LeadDO> selectByPersonIds(List<Long> personIds) {
        if (personIds == null || personIds.isEmpty()) return List.of();
        return selectList(new LambdaQueryWrapperX<LeadDO>()
                .in(LeadDO::getPersonId, personIds).orderByDesc(LeadDO::getSubmittedAt));
    }
    default List<LeadDO> selectByName(String name) {
        return selectList(new LambdaQueryWrapperX<LeadDO>()
                .eq(LeadDO::getSubmittedName, name).orderByDesc(LeadDO::getSubmittedAt));
    }
    default List<LeadDO> selectPendingByUserId(Long userId) {
        return selectList(new LambdaQueryWrapperX<LeadDO>()
                .eq(LeadDO::getAssignmentStatus, ASSIGNMENT_PENDING)
                .eq(LeadDO::getPendingAssigneeUserId, userId)
                .orderByAsc(LeadDO::getSubmittedAt));
    }
    default boolean existsPendingByUserId(Long userId) {
        return selectCount(new LambdaQueryWrapperX<LeadDO>()
                .eq(LeadDO::getAssignmentStatus, ASSIGNMENT_PENDING)
                .eq(LeadDO::getPendingAssigneeUserId, userId)) > 0;
    }
    default List<LeadDO> selectExpiredPending(LocalDateTime now) {
        return selectList(new LambdaQueryWrapperX<LeadDO>()
                .eq(LeadDO::getAssignmentStatus, ASSIGNMENT_PENDING)
                .le(LeadDO::getPendingExpiresAt, now)
                .orderByAsc(LeadDO::getPendingExpiresAt).last("LIMIT 100"));
    }
    default List<LeadDO> selectRetryableUnassignedAuto() {
        return selectList(new LambdaQueryWrapperX<LeadDO>()
                .eq(LeadDO::getDispatchMode, DISPATCH_AUTO)
                .eq(LeadDO::getAssignmentStatus, ASSIGNMENT_UNASSIGNED)
                .isNotNull(LeadDO::getAssignmentRuleSnapshot)
                .orderByAsc(LeadDO::getSubmittedAt).last("LIMIT 100"));
    }
    default List<LeadDO> selectPreQualificationNoProgressCandidates(LocalDateTime cutoff) {
        return selectList(new LambdaQueryWrapperX<LeadDO>()
                .eq(LeadDO::getStatus, "submitted")
                .eq(LeadDO::getAssignmentStatus, "owned")
                .isNotNull(LeadDO::getOwnerUserId)
                .and(query -> query.le(LeadDO::getLastFollowUpAt, cutoff)
                        .or(nested -> nested.isNull(LeadDO::getLastFollowUpAt)
                                .le(LeadDO::getOwnershipStartedAt, cutoff)))
                .orderByAsc(LeadDO::getOwnershipStartedAt).last("LIMIT 200"));
    }
    default int updateUnassignedToPending(Long id, Long assigneeId, LocalDateTime expiresAt, Integer attempt) {
        return update(null, new LambdaUpdateWrapper<LeadDO>()
                .eq(LeadDO::getId, id).eq(LeadDO::getAssignmentStatus, ASSIGNMENT_UNASSIGNED)
                .set(LeadDO::getAssignmentStatus, ASSIGNMENT_PENDING)
                .set(LeadDO::getPendingAssigneeUserId, assigneeId)
                .set(LeadDO::getPendingExpiresAt, expiresAt)
                .set(LeadDO::getAssignmentAttemptCount, attempt));
    }
    default PageResult<LeadDO> selectPublicPoolPage(PageParam pageParam, String keyword, List<Long> matchedLeadIds) {
        LambdaQueryWrapperX<LeadDO> query = new LambdaQueryWrapperX<LeadDO>()
                .eq(LeadDO::getAssignmentStatus, "public_pool");
        if (keyword != null && !keyword.isBlank()) applyLeadKeyword(query, keyword);
        if (matchedLeadIds != null) {
            if (matchedLeadIds.isEmpty()) query.eq(LeadDO::getId, -1L); else query.in(LeadDO::getId, matchedLeadIds);
        }
        return selectPage(pageParam, query
                .orderByAsc(LeadDO::getPublicPoolAt)
                .orderByAsc(LeadDO::getId));
    }
    default PageResult<LeadDO> selectPublicPoolPage(PageParam pageParam) {
        return selectPublicPoolPage(pageParam, null, null);
    }
    default int updatePublicPoolToOwned(Long id, Long ownerId) {
        return update(null, new LambdaUpdateWrapper<LeadDO>()
                .eq(LeadDO::getId, id).eq(LeadDO::getAssignmentStatus, "public_pool")
                .set(LeadDO::getAssignmentStatus, "owned")
                .set(LeadDO::getOwnerUserId, ownerId)
                .set(LeadDO::getCurrentAssignmentHistoryId, null)
                .set(LeadDO::getCurrentAssignmentFirstFollowUpAt, null)
                .set(LeadDO::getCurrentAssignmentFirstFollowUpDeadlineAt, null)
                .set(LeadDO::getNextFollowUpAt, null));
    }
    default int updatePendingResult(Long id, Long assigneeId, String newStatus, Long ownerId) {
        return update(null, new LambdaUpdateWrapper<LeadDO>()
                .eq(LeadDO::getId, id).eq(LeadDO::getAssignmentStatus, ASSIGNMENT_PENDING)
                .eq(LeadDO::getPendingAssigneeUserId, assigneeId)
                .set(LeadDO::getAssignmentStatus, newStatus)
                .set(LeadDO::getOwnerUserId, ownerId)
                .set(LeadDO::getPendingAssigneeUserId, null)
                .set(LeadDO::getPendingExpiresAt, null)
                .set(LeadDO::getCurrentAssignmentHistoryId, null)
                .set(LeadDO::getCurrentAssignmentFirstFollowUpAt, null)
                .set(LeadDO::getCurrentAssignmentFirstFollowUpDeadlineAt, null)
                .set(LeadDO::getNextFollowUpAt, null));
    }

    default List<LeadDO> selectExpiredQualifications(LocalDateTime now) {
        return selectList(new LambdaQueryWrapperX<LeadDO>()
                .eq(LeadDO::getStatus, "submitted")
                .eq(LeadDO::getAssignmentStatus, "owned")
                .isNotNull(LeadDO::getQualificationDeadlineAt)
                .le(LeadDO::getQualificationDeadlineAt, now)
                .orderByAsc(LeadDO::getQualificationDeadlineAt)
                .last("LIMIT 100"));
    }

    default PageResult<LeadDO> selectQualificationExceptionPage(PageParam pageParam, String type,
                                                                 Set<Long> managedOwnerIds,
                                                                 boolean manageAll, String keyword, List<Long> matchedLeadIds) {
        LambdaQueryWrapperX<LeadDO> query = new LambdaQueryWrapperX<>();
        if ("suspended".equals(type)) {
            query.eq(LeadDO::getStatus, "suspended");
            if (!manageAll) query.in(LeadDO::getOwnerUserId, managedOwnerIds);
        } else {
            query.eq(LeadDO::getAssignmentStatus, "recycle_pending");
            if (!manageAll) query.in(LeadDO::getRecycleSourceOwnerUserId, managedOwnerIds);
        }
        if (keyword != null && !keyword.isBlank()) applyLeadKeyword(query, keyword);
        if (matchedLeadIds != null) {
            if (matchedLeadIds.isEmpty()) query.eq(LeadDO::getId, -1L); else query.in(LeadDO::getId, matchedLeadIds);
        }
        query.orderByAsc(LeadDO::getSuspendedAt).orderByAsc(LeadDO::getId);
        return selectPage(pageParam, query);
    }
    default PageResult<LeadDO> selectQualificationExceptionPage(PageParam pageParam, String type,
                                                                 Set<Long> managedOwnerIds, boolean manageAll) {
        return selectQualificationExceptionPage(pageParam, type, managedOwnerIds, manageAll, null, null);
    }

    private static void applyLeadKeyword(LambdaQueryWrapperX<LeadDO> query, String rawKeyword) {
        String keyword = rawKeyword.trim();
        if (keyword.regionMatches(true, 0, "KZ", 0, 2)) {
            query.eq(LeadDO::getLeadNo, keyword.toUpperCase(java.util.Locale.ROOT));
            return;
        }
        if (keyword.chars().allMatch(Character::isDigit)) {
            try {
                query.eq(LeadDO::getId, Long.valueOf(keyword));
            } catch (NumberFormatException ex) {
                query.eq(LeadDO::getId, -1L);
            }
            return;
        }
        query.and(wrapper -> wrapper.like(LeadDO::getSubmittedName, keyword)
                .or().like(LeadDO::getSubmittedMobile, keyword)
                .or().like(LeadDO::getSubmittedWechatId, keyword));
    }
}
