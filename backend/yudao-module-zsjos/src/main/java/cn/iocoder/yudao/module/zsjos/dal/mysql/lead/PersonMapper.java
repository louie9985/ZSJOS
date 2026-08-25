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
    @Select("SELECT * FROM zsjos_person WHERE id=#{id} AND tenant_id=#{tenantId} AND deleted=b'0' FOR UPDATE")
    PersonDO selectByIdForUpdate(@Param("id") Long id, @Param("tenantId") Long tenantId);
    @Select("<script>SELECT * FROM zsjos_person WHERE deleted=b'0' AND ("
            + "<if test='mobile != null'>(CAST(mobile AS BINARY)=CAST(#{mobile} AS BINARY) "
            + "OR CAST(wechat_id AS BINARY)=CAST(#{mobile} AS BINARY))</if>"
            + "<if test='mobile != null and wechatId != null'> OR </if>"
            + "<if test='wechatId != null'>(CAST(wechat_id AS BINARY)=CAST(#{wechatId} AS BINARY) "
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
        if (reqVO.getServiceStatus() == null) {
            query.apply("EXISTS (SELECT 1 FROM zsjos_service_relation sr WHERE sr.person_id=zsjos_person.id "
                            + "AND sr.tenant_id=zsjos_person.tenant_id AND sr.deleted=b'0' "
                            + "AND ((sr.owner_user_id={0} AND sr.status IN ('active','paused','completed')) "
                            + "OR ((sr.content_director_user_id={0} OR sr.career_planner_user_id={0}) "
                            + "AND sr.status='active' AND sr.acceptance_status='accepted')))", userId);
        } else {
            query.apply("EXISTS (SELECT 1 FROM zsjos_service_relation sr WHERE sr.person_id=zsjos_person.id "
                            + "AND sr.tenant_id=zsjos_person.tenant_id AND sr.deleted=b'0' "
                            + "AND ((sr.owner_user_id={0} AND sr.status={1}) "
                            + "OR ((sr.content_director_user_id={0} OR sr.career_planner_user_id={0}) "
                            + "AND sr.status={1} AND sr.acceptance_status='accepted')))",
                    userId, reqVO.getServiceStatus());
        }
        return selectPage(reqVO, query.orderByDesc(lastActivityExpression()).orderByDesc("id"));
    }

    default PageResult<PersonDO> selectMediaStudentPage(MyStudentPageReqVO reqVO, Long userId) {
        QueryWrapperX<PersonDO> query = studentQuery(reqVO, null);
        query.apply("EXISTS (SELECT 1 FROM zsjos_service_relation sr WHERE sr.person_id=zsjos_person.id "
                + "AND sr.tenant_id=zsjos_person.tenant_id AND sr.deleted=b'0' "
                + "AND ((sr.owner_user_id={0} AND sr.status IN ('active','paused','completed')) "
                + "OR ((sr.content_director_user_id={0} OR sr.career_planner_user_id={0} OR sr.operator_user_id={0}) "
                + "AND sr.status='active' AND sr.acceptance_status='accepted')))" , userId);
        return selectPage(reqVO, query.orderByDesc(lastActivityExpression()).orderByDesc("id"));
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
