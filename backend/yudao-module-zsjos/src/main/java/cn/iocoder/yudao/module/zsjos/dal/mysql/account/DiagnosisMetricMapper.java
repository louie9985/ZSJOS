package cn.iocoder.yudao.module.zsjos.dal.mysql.account;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.account.DiagnosisMetricDO;
import org.apache.ibatis.annotations.*;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Mapper
public interface DiagnosisMetricMapper extends BaseMapperX<DiagnosisMetricDO> {
    @Select("SELECT * FROM zsjos_account_diagnosis_metric WHERE tenant_id=#{tenant} AND account_id=#{account} AND node_key=#{node} AND interval_days=#{days} AND deleted=0 FOR UPDATE")
    DiagnosisMetricDO lock(@Param("tenant") Long tenant, @Param("account") Long account, @Param("node") String node, @Param("days") int days);

    @Insert("INSERT IGNORE INTO zsjos_account_diagnosis_member(tenant_id,metric_id,lead_id) VALUES(#{tenant},#{metric},#{lead})")
    void member(@Param("tenant") Long tenant,@Param("metric") Long metric,@Param("lead") Long lead);

    @Select("SELECT COUNT(DISTINCT m.lead_id) FROM zsjos_account_diagnosis_member m WHERE m.tenant_id=#{tenant} AND m.metric_id=#{metric} AND EXISTS(SELECT 1 FROM zsjos_order o WHERE o.tenant_id=m.tenant_id AND o.lead_id=m.lead_id AND o.deleted=0 AND o.status='effective' AND o.order_type<>'repurchase' AND o.effective_at IS NOT NULL AND o.effective_at<=#{now})")
    long deals(@Param("tenant") Long tenant,@Param("metric") Long metric,@Param("now") LocalDateTime now);

    @Select("SELECT id,status,deleted,update_time FROM zsjos_lead WHERE tenant_id=#{tenant} AND partner_id=#{partner} AND submitted_at >= #{from} AND submitted_at < #{to}")
    List<Map<String,Object>> cohort(@Param("tenant") Long tenant,@Param("partner") Long partner,@Param("from") LocalDateTime from,@Param("to") LocalDateTime to);

    // Frozen submission snapshots prevent a later draft edit from changing an earlier publication period.
    @Select("""
        <script>
        WITH versions AS (
          SELECT i.content_id,i.content_snapshot_json,b.submitted_at,i.id,
                 ROW_NUMBER() OVER(PARTITION BY i.content_id ORDER BY b.submitted_at DESC,i.id DESC) rn
          FROM zsjos_content_review_batch b JOIN zsjos_content_review_batch_item i
            ON i.batch_id=b.id AND i.tenant_id=b.tenant_id AND i.deleted=0
          WHERE b.tenant_id=#{tenant} AND b.deleted=0 AND b.submitted_at IS NOT NULL
            AND b.submitted_at &lt; #{cutoff} AND b.status NOT IN ('DRAFT','VOID')
            AND (b.status NOT IN ('CANCELLED','CANCELED') OR b.finalized_at &gt;= #{cutoff})
            AND (JSON_CONTAINS(COALESCE(NULLIF(b.account_ids_json,''),'[]'),CAST(#{account} AS JSON))
              OR ((b.account_ids_json IS NULL OR b.account_ids_json='' OR b.account_ids_json='[]') AND b.account_id=#{account}))
        ), times AS (
          SELECT STR_TO_DATE(REPLACE(JSON_UNQUOTE(JSON_EXTRACT(content_snapshot_json,'$.plannedPublishAt')),'T',' '),'%Y-%m-%d %H:%i:%s') planned
          FROM versions WHERE rn=1
        )
        SELECT COALESCE(SUM(planned &gt;= #{from} AND planned &lt; #{to}),0) published,
               COALESCE(SUM(planned IS NULL),0) missing FROM times
        </script>
        """)
    Map<String,Object> publications(@Param("tenant") Long tenant,@Param("account") Long account,
         @Param("from") LocalDateTime from,@Param("to") LocalDateTime to,@Param("cutoff") LocalDateTime cutoff);

    // Both windows use the same cutoff and latest submitted version, including missing-time evidence.
    @Select("""
        <script>
        WITH versions AS (
          SELECT i.content_id,i.content_snapshot_json,b.submitted_at,i.id,
                 ROW_NUMBER() OVER(PARTITION BY i.content_id ORDER BY b.submitted_at DESC,i.id DESC) rn
          FROM zsjos_content_review_batch b JOIN zsjos_content_review_batch_item i
            ON i.batch_id=b.id AND i.tenant_id=b.tenant_id AND i.deleted=0
          WHERE b.tenant_id=#{tenant} AND b.deleted=0 AND b.submitted_at IS NOT NULL
            AND b.submitted_at &lt; #{cutoff} AND b.status NOT IN ('DRAFT','VOID')
            AND (b.status NOT IN ('CANCELLED','CANCELED') OR b.finalized_at &gt;= #{cutoff})
            AND (JSON_CONTAINS(COALESCE(NULLIF(b.account_ids_json,''),'[]'),CAST(#{account} AS JSON))
              OR ((b.account_ids_json IS NULL OR b.account_ids_json='' OR b.account_ids_json='[]') AND b.account_id=#{account}))
        ), times AS (
          SELECT STR_TO_DATE(REPLACE(JSON_UNQUOTE(JSON_EXTRACT(content_snapshot_json,'$.plannedPublishAt')),'T',' '),'%Y-%m-%d %H:%i:%s') planned
          FROM versions WHERE rn=1
        )
        SELECT COALESCE(SUM(planned &gt;= #{from7} AND planned &lt; #{to}),0) published7,
               COALESCE(SUM(planned &gt;= #{from28} AND planned &lt; #{to}),0) published28,
               COALESCE(SUM(planned IS NULL),0) missing FROM times
        </script>
        """)
    Map<String,Object> publicationPeriods(@Param("tenant") Long tenant,@Param("account") Long account,
         @Param("from7") LocalDateTime from7,@Param("from28") LocalDateTime from28,
         @Param("to") LocalDateTime to,@Param("cutoff") LocalDateTime cutoff);

    @Select("SELECT related_object_refs FROM zsjos_business_event WHERE tenant_id=#{tenant} AND aggregate_type='lead' AND aggregate_id=#{lead} AND event_type='lead_flow_operation' AND occurred_at < #{cutoff} AND deleted=0 ORDER BY occurred_at DESC,id DESC")
    List<String> flowBefore(@Param("tenant") Long tenant,@Param("lead") Long lead,@Param("cutoff") LocalDateTime cutoff);

    @Select("SELECT to_status FROM zsjos_business_event WHERE tenant_id=#{tenant} AND aggregate_type='lead' AND aggregate_id=#{lead} AND event_type='lead_flow_operation' AND to_status IS NOT NULL AND occurred_at < #{cutoff} AND deleted=0 ORDER BY occurred_at DESC,id DESC LIMIT 1")
    String statusBefore(@Param("tenant") Long tenant,@Param("lead") Long lead,@Param("cutoff") LocalDateTime cutoff);

    @Select("SELECT COUNT(DISTINCT o.lead_id) FROM zsjos_order o WHERE o.tenant_id=#{tenant} AND o.lead_id=#{lead} AND o.deleted=0 AND o.status='effective' AND o.order_type<>'repurchase' AND o.effective_at IS NOT NULL AND o.effective_at<=#{now}")
    long leadDeals(@Param("tenant") Long tenant,@Param("lead") Long lead,@Param("now") LocalDateTime now);
    @Select("""
        <script>
        SELECT leadId,toStatus FROM (
          SELECT aggregate_id leadId,to_status toStatus,
                 ROW_NUMBER() OVER(PARTITION BY aggregate_id ORDER BY occurred_at DESC,id DESC) rn
          FROM zsjos_business_event WHERE tenant_id=#{tenant} AND aggregate_type='lead'
            AND event_type='lead_flow_operation' AND to_status IS NOT NULL AND occurred_at &lt; #{cutoff} AND deleted=0
            AND aggregate_id IN <foreach collection='ids' item='id' open='(' separator=',' close=')'>#{id}</foreach>
        ) states WHERE rn=1
        </script>
        """)
    List<Map<String,Object>> statusesBefore(@Param("tenant") Long tenant,@Param("ids") List<Long> ids,@Param("cutoff") LocalDateTime cutoff);

    @Select("""
        <script>
        SELECT aggregate_id leadId,related_object_refs refs FROM zsjos_business_event
        WHERE tenant_id=#{tenant} AND aggregate_type='lead' AND event_type='lead_flow_operation'
          AND occurred_at &lt; #{cutoff} AND deleted=0
          AND aggregate_id IN <foreach collection='ids' item='id' open='(' separator=',' close=')'>#{id}</foreach>
        ORDER BY aggregate_id,occurred_at DESC,id DESC
        </script>
        """)
    List<Map<String,Object>> flowsBefore(@Param("tenant") Long tenant,@Param("ids") List<Long> ids,@Param("cutoff") LocalDateTime cutoff);

    @Select("""
        <script>
        SELECT DISTINCT lead_id FROM zsjos_order WHERE tenant_id=#{tenant} AND deleted=0
          AND status='effective' AND order_type&lt;&gt;'repurchase' AND effective_at IS NOT NULL AND effective_at&lt;=#{now}
          AND lead_id IN <foreach collection='ids' item='id' open='(' separator=',' close=')'>#{id}</foreach>
        </script>
        """)
    List<Long> dealtLeadIds(@Param("tenant") Long tenant,@Param("ids") List<Long> ids,@Param("now") LocalDateTime now);

    @Insert("""
        <script>
        INSERT IGNORE INTO zsjos_account_diagnosis_member(tenant_id,metric_id,lead_id) VALUES
        <foreach collection='ids' item='id' separator=','>(#{tenant},#{metric},#{id})</foreach>
        </script>
        """)
    void members(@Param("tenant") Long tenant,@Param("metric") Long metric,@Param("ids") List<Long> ids);

}
