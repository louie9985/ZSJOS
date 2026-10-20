package cn.iocoder.yudao.module.zsjos.dal.mysql.lead;

import cn.iocoder.yudao.module.zsjos.dal.dataobject.event.BusinessEventDO;
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
            + "NOT IN ('sales_self_sourced_auto','education_self_sourced_auto') "
            + "AND e.operator_user_id IN <foreach collection='users' item='user' open='(' separator=',' close=')'>#{user}</foreach> "
            + "AND EXISTS (SELECT 1 FROM zsjos_lead_assignment_history h WHERE h.tenant_id=e.tenant_id AND h.deleted=0 "
            + "AND h.lead_id=e.aggregate_id AND h.owner_identity_snapshot='sales' AND h.to_owner_user_id=e.operator_user_id "
            + "AND h.id=(SELECT x.id FROM zsjos_lead_assignment_history x WHERE x.tenant_id=e.tenant_id AND x.deleted=0 "
            + "AND x.lead_id=e.aggregate_id AND x.occurred_at &lt;= e.occurred_at "
            + "AND x.action_type IN ('accept','claim','transfer','recycle','release','close') "
            + "ORDER BY x.occurred_at DESC,x.id DESC LIMIT 1))</script>")
    List<BusinessEventDO> selectQualifications(@Param("tenant") Long tenant, @Param("users") List<Long> users,
                                               @Param("start") LocalDateTime start, @Param("end") LocalDateTime end);
}
