package cn.iocoder.yudao.module.zsjos.dal.mysql.educationteam;

import cn.iocoder.yudao.module.zsjos.dal.dataobject.event.BusinessEventDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.LeadFollowUpRecordDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.registration.StudentContactRecordDO;
import org.apache.ibatis.annotations.*;
import java.time.LocalDateTime;
import java.util.List;

/** Event-time work attribution is independent of current ownership and provider attribution. */
@Mapper
public interface EducationTeamDailyMapper {
    String USERS = "<foreach collection='users' item='user' open='(' separator=',' close=')'>#{user}</foreach>";

    @Select("<script>SELECT e.operator_user_id, e.aggregate_id FROM zsjos_business_event e "
            + "JOIN zsjos_lead l ON l.id=e.aggregate_id AND l.tenant_id=e.tenant_id AND l.deleted=0 "
            + "WHERE e.tenant_id=#{tenant} AND e.deleted=0 AND e.aggregate_type='lead' "
            + "AND e.event_type='lead_flow_operation' AND e.idempotency_key=CONCAT('lead-flow:submission:',l.id) "
            + "AND l.submission_source_type='education_self_sourced' "
            + "AND l.submitted_at &gt;= #{start} AND l.submitted_at &lt; #{end} "
            + "AND e.operator_user_id IN " + USERS + " GROUP BY e.operator_user_id,e.aggregate_id</script>")
    List<BusinessEventDO> selectNewIntentions(@Param("tenant") Long tenant, @Param("users") List<Long> users,
                                             @Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    @Select("<script>SELECT f.operator_user_id FROM ("
            + "SELECT r.operator_user_id,r.lead_id,r.tenant_id,r.occurred_at,r.owner_identity_snapshot FROM zsjos_lead_follow_up_record r "
            + "WHERE r.deleted=0 AND r.tenant_id=#{tenant} AND NOT EXISTS (SELECT 1 FROM zsjos_business_event a "
            + "WHERE a.tenant_id=r.tenant_id AND a.deleted=0 AND a.aggregate_type='lead' AND a.aggregate_id=r.lead_id "
            + "AND a.event_type='lead_follow_up_recorded' AND JSON_EXTRACT(a.related_object_refs,'$.followUpRecordId')=r.id "
            + "AND JSON_UNQUOTE(JSON_EXTRACT(a.related_object_refs,'$.generationSource')) IN ('sales_self_sourced_auto','education_self_sourced_auto')) "
            + "UNION ALL SELECT r.operator_user_id,r.lead_id,r.tenant_id,r.occurred_at,r.owner_identity_snapshot "
            + "FROM zsjos_opportunity_follow_up_record r WHERE r.deleted=0 AND r.tenant_id=#{tenant}) f "
            + "JOIN zsjos_lead l ON l.id=f.lead_id AND l.tenant_id=f.tenant_id AND l.deleted=0 "
            + "WHERE f.owner_identity_snapshot='education' AND l.submitted_at &lt; #{start} "
            + "AND f.occurred_at &gt;= #{start} AND f.occurred_at &lt; #{end} AND f.operator_user_id IN " + USERS + "</script>")
    List<LeadFollowUpRecordDO> selectOldIntentionFollowUps(@Param("tenant") Long tenant, @Param("users") List<Long> users,
                                                         @Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    @Select("<script>SELECT r.operator_user_id,MIN(r.id) id FROM zsjos_student_contact_record r "
            + "JOIN zsjos_service_relation s ON s.id=r.service_relation_id AND s.tenant_id=r.tenant_id "
            + "WHERE r.tenant_id=#{tenant} AND r.deleted=0 AND s.person_id IS NOT NULL "
            + "AND r.contact_type IN ('student_first_contact','student_study_plan','student_contact') "
            + "AND r.submitted_at &gt;= #{start} AND r.submitted_at &lt; #{end} AND r.operator_user_id IN " + USERS
            + " GROUP BY r.operator_user_id,s.person_id</script>")
    List<StudentContactRecordDO> selectContactedStudents(@Param("tenant") Long tenant, @Param("users") List<Long> users,
                                                       @Param("start") LocalDateTime start, @Param("end") LocalDateTime end);
}
