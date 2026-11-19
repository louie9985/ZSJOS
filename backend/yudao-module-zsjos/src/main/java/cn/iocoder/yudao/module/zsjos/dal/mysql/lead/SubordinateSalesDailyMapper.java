package cn.iocoder.yudao.module.zsjos.dal.mysql.lead;

import cn.iocoder.yudao.module.zsjos.dal.dataobject.event.BusinessEventDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.LeadFollowUpRecordDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.registration.StudentContactRecordDO;
import org.apache.ibatis.annotations.*;
import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface SubordinateSalesDailyMapper {
    /** Qualification events predate identity payloads; use the last proven assignment at that time. */
    @Select("<script>SELECT e.operator_user_id,e.aggregate_id FROM zsjos_business_event e "
            + "WHERE e.tenant_id=#{tenant} AND e.deleted=0 AND e.aggregate_type='lead' AND e.event_type='lead_qualified_valid' "
            + "AND e.occurred_at &gt;= #{start} AND e.occurred_at &lt; #{end} "
            + "AND COALESCE(JSON_UNQUOTE(JSON_EXTRACT(e.related_object_refs,'$.generationSource')),'') "
            + "NOT IN ('sales_self_sourced_auto') "
            + "AND e.operator_user_id IN <foreach collection='users' item='user' open='(' separator=',' close=')'>#{user}</foreach> "
            + "AND EXISTS (SELECT 1 FROM zsjos_lead_assignment_history h WHERE h.tenant_id=e.tenant_id AND h.deleted=0 "
            + "AND h.lead_id=e.aggregate_id AND h.owner_identity_snapshot='sales' AND h.to_owner_user_id=e.operator_user_id "
            + "AND h.id=(SELECT x.id FROM zsjos_lead_assignment_history x WHERE x.tenant_id=e.tenant_id AND x.deleted=0 "
            + "AND x.lead_id=e.aggregate_id AND x.occurred_at &lt;= e.occurred_at "
            + "AND x.action_type IN ('accept','claim','transfer','recycle','release','close') "
            + "ORDER BY x.occurred_at DESC,x.id DESC LIMIT 1))</script>")
    List<BusinessEventDO> selectQualifications(@Param("tenant") Long tenant, @Param("users") List<Long> users,
                                               @Param("start") LocalDateTime start, @Param("end") LocalDateTime end);
    String USERS = "<foreach collection='users' item='user' open='(' separator=',' close=')'>#{user}</foreach>";

    @Select("<script>SELECT e.operator_user_id, e.aggregate_id FROM zsjos_business_event e "
            + "JOIN zsjos_lead l ON l.id=e.aggregate_id AND l.tenant_id=e.tenant_id AND l.deleted=0 "
            + "WHERE e.tenant_id=#{tenant} AND e.deleted=0 AND e.aggregate_type='lead' "
            + "AND e.event_type='lead_flow_operation' AND e.idempotency_key=CONCAT('lead-flow:submission:',l.id) "
            + "AND l.submission_source_type='sales_self_sourced' "
            + "AND l.submitted_at &gt;= #{start} AND l.submitted_at &lt; #{end} "
            + "AND e.operator_user_id IN " + USERS + " GROUP BY e.operator_user_id,e.aggregate_id</script>")
    List<BusinessEventDO> selectNewIntentions(@Param("tenant") Long tenant, @Param("users") List<Long> users,
                                             @Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    @Select("<script>SELECT f.operator_user_id FROM ("
            + "SELECT r.operator_user_id,r.lead_id,r.tenant_id,r.occurred_at,r.owner_identity_snapshot FROM zsjos_lead_follow_up_record r "
            + "WHERE r.deleted=0 AND r.tenant_id=#{tenant} AND NOT EXISTS (SELECT 1 FROM zsjos_business_event a "
            + "WHERE a.tenant_id=r.tenant_id AND a.deleted=0 AND a.aggregate_type='lead' AND a.aggregate_id=r.lead_id "
            + "AND a.event_type='lead_follow_up_recorded' AND JSON_EXTRACT(a.related_object_refs,'$.followUpRecordId')=r.id "
            + "AND JSON_UNQUOTE(JSON_EXTRACT(a.related_object_refs,'$.generationSource'))='sales_self_sourced_auto') "
            + "UNION ALL SELECT r.operator_user_id,r.lead_id,r.tenant_id,r.occurred_at,r.owner_identity_snapshot "
            + "FROM zsjos_opportunity_follow_up_record r WHERE r.deleted=0 AND r.tenant_id=#{tenant}) f "
            + "JOIN zsjos_lead l ON l.id=f.lead_id AND l.tenant_id=f.tenant_id AND l.deleted=0 "
            + "WHERE f.owner_identity_snapshot='sales' AND l.submitted_at &lt; #{start} "
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
