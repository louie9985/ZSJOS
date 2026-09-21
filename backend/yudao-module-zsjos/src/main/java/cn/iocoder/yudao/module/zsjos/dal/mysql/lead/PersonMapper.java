package cn.iocoder.yudao.module.zsjos.dal.mysql.lead;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.PersonDO;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.query.QueryWrapperX;
import cn.iocoder.yudao.module.zsjos.controller.admin.registration.vo.MyStudentPageReqVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Mapper
public interface PersonMapper extends BaseMapperX<PersonDO> {
    String MEDIA_PERSON_PREDICATE = "(EXISTS (SELECT 1 FROM zsjos_service_relation ms WHERE ms.person_id=zsjos_person.id "
            + "AND ms.tenant_id=zsjos_person.tenant_id AND ms.deleted=b'0' AND (ms.content_director_user_id IS NOT NULL "
            + "OR ms.career_planner_user_id IS NOT NULL OR ms.operator_user_id IS NOT NULL)) "
            + "OR EXISTS (SELECT 1 FROM zsjos_media_account ma WHERE ma.student_person_id=zsjos_person.id AND ma.tenant_id=zsjos_person.tenant_id AND ma.deleted=b'0') "
            + "OR EXISTS (SELECT 1 FROM zsjos_positioning_card pc WHERE pc.student_person_id=zsjos_person.id AND pc.tenant_id=zsjos_person.tenant_id AND pc.deleted=b'0') "
            + "OR EXISTS (SELECT 1 FROM zsjos_student_positioning_interview pi WHERE pi.student_person_id=zsjos_person.id AND pi.tenant_id=zsjos_person.tenant_id AND pi.deleted=b'0'))";

    default PageResult<PersonDO> selectAllMediaStudentPage(MyStudentPageReqVO req, java.util.Collection<Long> matchedIds) {
        QueryWrapperX<PersonDO> query = studentQuery(req, matchedIds);
        query.apply(MEDIA_PERSON_PREDICATE);
        if (req.getClassId() != null || req.getServiceStatus() != null) {
            query.apply(STUDENT_RELATION_PREDICATE
                    + "AND ({0} IS NULL OR sr.class_id={0}) AND ({1} IS NULL OR sr.status={1}) AND "
                    + cn.iocoder.yudao.module.zsjos.dal.mysql.registration.ServiceRelationMapper.MEDIA_RELATION_PREDICATE
                            .replace("zsjos_service_relation", "sr") + ")",
                    req.getClassId(), req.getServiceStatus());
        }
        return selectPage(req, query.orderByDesc(lastActivityExpression()).orderByDesc("id"));
    }

    default boolean existsMediaStudent(Long personId) {
        return selectCount(new QueryWrapperX<PersonDO>().eq("id", personId).apply(MEDIA_PERSON_PREDICATE)) > 0;
    }

    default PageResult<PersonDO> selectTenantReadStudentPage(MyStudentPageReqVO req, java.util.Collection<Long> matchedIds) {
        QueryWrapperX<PersonDO> query = studentQuery(req, matchedIds);
        query.apply(STUDENT_RELATION_PREDICATE
                + "AND ({0} IS NULL OR sr.class_id={0}) AND ({1} IS NULL OR sr.status={1}))",
                req.getClassId(), req.getServiceStatus());
        return selectPage(req, query.orderByDesc(lastActivityExpression()).orderByDesc("id"));
    }
    /**
     * Start of every student-page EXISTS clause: the person's live service relations. Each caller appends
     * its own scope clause (class id, then the owner list where one applies) and then the status condition.
     */
    String STUDENT_RELATION_PREDICATE =
            "EXISTS (SELECT 1 FROM zsjos_service_relation sr WHERE sr.person_id=zsjos_person.id "
                    + "AND sr.tenant_id=zsjos_person.tenant_id AND sr.deleted=b'0' ";

    /** A SQL fragment together with its positional arguments, kept in one value so the two cannot drift. */
    record StudentScopePredicate(String sql, Object[] args) {
    }

    @Select("SELECT * FROM zsjos_person WHERE id=#{id} AND tenant_id=#{tenantId} AND deleted=b'0' FOR UPDATE")
    PersonDO selectByIdForUpdate(@Param("id") Long id, @Param("tenantId") Long tenantId);
    @Select("<script>SELECT * FROM zsjos_person WHERE deleted=b'0' AND ("
            + "<if test='mobile != null'>(CAST(mobile AS BINARY)=CAST(#{mobile} AS BINARY) "
            + "OR LOWER(wechat_id)=LOWER(#{mobile}))</if>"
            + "<if test='mobile != null and wechatId != null'> OR </if>"
            + "<if test='wechatId != null'>(LOWER(wechat_id)=LOWER(#{wechatId}) "
            + "OR CAST(mobile AS BINARY)=CAST(#{wechatId} AS BINARY))</if>"
            + ")</script>")
    List<PersonDO> selectDuplicateCandidates(@Param("mobile") String mobile, @Param("wechatId") String wechatId);

    default PageResult<PersonDO> selectStudentPage(MyStudentPageReqVO reqVO, java.util.Collection<Long> visibleIds,
                                                   java.util.Collection<Long> matchedIds) {
        QueryWrapperX<PersonDO> query = new QueryWrapperX<>();
        if (visibleIds == null || visibleIds.isEmpty()) query.eq("id", -1L);
        else query.in("id", visibleIds);
        if (matchedIds != null) {
            if (matchedIds.isEmpty()) query.eq("id", -1L);
            else query.in("id", matchedIds);
        }
        if (reqVO.getKeyword() != null && !reqVO.getKeyword().isBlank()) {
            String keyword = reqVO.getKeyword().trim();
            query.and(value -> value.like("name", keyword).or().like("mobile", keyword)
                    .or().like("wechat_id", keyword)
                    .or().apply("EXISTS (SELECT 1 FROM zsjos_lead sl WHERE sl.person_id=zsjos_person.id "
                            + "AND sl.tenant_id=zsjos_person.tenant_id AND sl.deleted=b'0' "
                            + "AND sl.lead_no LIKE CONCAT('%',{0},'%'))", keyword));
        }
        return selectPage(reqVO, query.orderByDesc(lastActivityExpression()).orderByDesc("id"));
    }

    default PageResult<PersonDO> selectMyStudentPage(MyStudentPageReqVO reqVO, Long userId,
                                                      java.util.Collection<Long> matchedIds) {
        QueryWrapperX<PersonDO> query = studentQuery(reqVO, matchedIds);
        String classPredicate = " AND ({1} IS NULL OR sr.class_id={1})";
        if (reqVO.getServiceStatus() == null) {
            query.apply("EXISTS (SELECT 1 FROM zsjos_service_relation sr WHERE sr.person_id=zsjos_person.id "
                            + "AND sr.tenant_id=zsjos_person.tenant_id AND sr.deleted=b'0' "
                            + classPredicate
                            + "AND ((sr.owner_user_id={0} AND sr.status IN ('active','paused','completed')) "
                            + "OR ((sr.content_director_user_id={0} OR sr.career_planner_user_id={0}) "
                            + "AND sr.status='active' AND sr.acceptance_status='accepted')))", userId, reqVO.getClassId());
        } else {
            query.apply("EXISTS (SELECT 1 FROM zsjos_service_relation sr WHERE sr.person_id=zsjos_person.id "
                            + "AND sr.tenant_id=zsjos_person.tenant_id AND sr.deleted=b'0' "
                            + classPredicate
                            + "AND ((sr.owner_user_id={0} AND sr.status={2}) "
                            + "OR ((sr.content_director_user_id={0} OR sr.career_planner_user_id={0}) "
                            + "AND sr.status={2} AND sr.acceptance_status='accepted')))",
                    userId, reqVO.getClassId(), reqVO.getServiceStatus());
        }
        return selectPage(reqVO, query.orderByDesc(lastActivityExpression()).orderByDesc("id"));
    }

    default PageResult<PersonDO> selectMediaStudentPage(MyStudentPageReqVO reqVO, Long userId) {
        return selectMediaStudentPage(reqVO, userId, null);
    }

    default PageResult<PersonDO> selectMediaStudentPage(MyStudentPageReqVO reqVO, Long userId,
                                                       java.util.Collection<Long> matchedIds) {
        QueryWrapperX<PersonDO> query = studentQuery(reqVO, matchedIds);
        query.apply("EXISTS (SELECT 1 FROM zsjos_service_relation sr WHERE sr.person_id=zsjos_person.id "
                + "AND sr.tenant_id=zsjos_person.tenant_id AND sr.deleted=b'0' "
                + "AND ({1} IS NULL OR sr.class_id={1}) AND ({2} IS NULL OR sr.status={2}) "
                + "AND ((sr.owner_user_id={0} AND sr.status IN ('active','paused','completed')) "
                + "OR ((sr.content_director_user_id={0} OR sr.career_planner_user_id={0} OR sr.operator_user_id={0}) "
                + "AND sr.status='active' AND sr.acceptance_status='accepted')))" , userId, reqVO.getClassId(), reqVO.getServiceStatus());
        return selectPage(reqVO, query.orderByDesc(lastActivityExpression()).orderByDesc("id"));
    }

    /**
     * Tenant-wide student read, used only when the reader's data scope covers every department.
     */
    default PageResult<PersonDO> selectAllStudentPage(MyStudentPageReqVO reqVO,
                                                      java.util.Collection<Long> matchedIds) {
        QueryWrapperX<PersonDO> query = studentQuery(reqVO, matchedIds);
        applyStatusPredicate(query, reqVO, "AND ({0} IS NULL OR sr.class_id={0}) AND ");
        return selectPage(reqVO, query.orderByDesc(lastActivityExpression()).orderByDesc("id"));
    }

    /**
     * Department-scoped student read. Visibility follows the service owner only, resolved live from
     * the owner's department rather than a stored snapshot, so a homeroom transfer moves the student
     * with its owner. Collaborator roles are deliberately excluded: they belong to other business lines.
     */
    default PageResult<PersonDO> selectManagedStudentPage(MyStudentPageReqVO reqVO,
                                                           java.util.Collection<Long> ownerUserIds,
                                                           java.util.Collection<Long> matchedIds) {
        QueryWrapperX<PersonDO> query = studentQuery(reqVO, matchedIds);
        if (ownerUserIds == null || ownerUserIds.isEmpty()) {
            return PageResult.empty();
        }
        String owners = ownerUserIds.stream().map(String::valueOf).collect(java.util.stream.Collectors.joining(","));
        applyStatusPredicate(query, reqVO,
                "AND ({0} IS NULL OR sr.class_id={0}) AND sr.owner_user_id IN (" + owners + ") AND ");
        return selectPage(reqVO, query.orderByDesc(lastActivityExpression()).orderByDesc("id"));
    }

    /**
     * Appends the service-status condition to a class predicate. The SQL is branched rather than the
     * argument list: MyBatis Plus rewrites "{n}" placeholders by position and rejects an argument whose
     * placeholder is absent, so a null status must not carry a second argument (it never resolves "{1}").
     */
    private static void applyStatusPredicate(QueryWrapperX<PersonDO> query, MyStudentPageReqVO reqVO,
                                             String scopeClause) {
        StudentScopePredicate predicate = buildStudentScopePredicate(
                STUDENT_RELATION_PREDICATE + scopeClause, reqVO.getClassId(), reqVO.getServiceStatus());
        query.apply(predicate.sql(), predicate.args());
    }

    /** The SQL fragment plus its positional arguments, kept together so the two can never drift. */
    static StudentScopePredicate buildStudentScopePredicate(String prefix, Long classId, String serviceStatus) {
        return serviceStatus == null
                ? new StudentScopePredicate(prefix + "sr.status IN ('active','paused','completed'))", new Object[]{classId})
                : new StudentScopePredicate(prefix + "sr.status={1})", new Object[]{classId, serviceStatus});
    }

    private static QueryWrapperX<PersonDO> studentQuery(MyStudentPageReqVO reqVO,
                                                         java.util.Collection<Long> matchedIds) {
        QueryWrapperX<PersonDO> query = new QueryWrapperX<>();
        if (matchedIds != null) {
            if (matchedIds.isEmpty()) query.eq("id", -1L);
            else query.in("id", matchedIds);
        }
        if (reqVO.getKeyword() != null && !reqVO.getKeyword().isBlank()) {
            String keyword = reqVO.getKeyword().trim();
            query.and(value -> value.like("name", keyword).or().like("mobile", keyword)
                    .or().like("wechat_id", keyword)
                    .or().apply("EXISTS (SELECT 1 FROM zsjos_lead sl WHERE sl.person_id=zsjos_person.id "
                            + "AND sl.tenant_id=zsjos_person.tenant_id AND sl.deleted=b'0' "
                            + "AND sl.lead_no LIKE CONCAT('%',{0},'%'))", keyword));
        }
        return query;
    }

    private static String lastActivityExpression() {
        return "GREATEST(COALESCE(zsjos_person.update_time,'1970-01-01'),"
                + "COALESCE((SELECT MAX(sr.update_time) FROM zsjos_service_relation sr WHERE sr.person_id=zsjos_person.id "
                + "AND sr.tenant_id=zsjos_person.tenant_id AND sr.deleted=b'0'),'1970-01-01'),"
                + "COALESCE((SELECT MAX(ma.update_time) FROM zsjos_media_account ma WHERE ma.student_person_id=zsjos_person.id "
                + "AND ma.tenant_id=zsjos_person.tenant_id AND ma.deleted=b'0'),'1970-01-01'),"
                + "COALESCE((SELECT MAX(mc.update_time) FROM zsjos_content mc JOIN zsjos_media_account ma2 ON ma2.id=mc.account_id "
                + "AND ma2.tenant_id=mc.tenant_id AND ma2.deleted=b'0' WHERE ma2.student_person_id=zsjos_person.id "
                + "AND mc.tenant_id=zsjos_person.tenant_id AND mc.deleted=b'0'),'1970-01-01'),"
                + "COALESCE((SELECT MAX(pc.update_time) FROM zsjos_positioning_card pc WHERE pc.student_person_id=zsjos_person.id "
                + "AND pc.tenant_id=zsjos_person.tenant_id AND pc.deleted=b'0'),'1970-01-01'),"
                + "COALESCE((SELECT MAX(tr.update_time) FROM zsjos_media_student_talk_record tr WHERE tr.student_person_id=zsjos_person.id "
                + "AND tr.tenant_id=zsjos_person.tenant_id AND tr.deleted=b'0'),'1970-01-01'))";
    }
}
