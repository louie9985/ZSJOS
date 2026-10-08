package cn.iocoder.yudao.module.zsjos.dal.mysql.performance;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface MediaLeadFactMapper {
    // 单笔订单总金额（元）达到门槛才计成交；总览与明细共用，不跨订单累计。
    String CONVERSION_AMOUNT_CONDITION = " AND o.total_amount &gt;= 1280 ";

    @Select("""
        <script>
        SELECT l.id,l.lead_no leadNo,l.contribution_user_id_snapshot userId,
               l.contribution_user_name_snapshot userName,l.contribution_dept_id_snapshot deptId,
               l.submitted_at submittedAt,l.status,l.source_channel_label_snapshot channelLabel,
               l.lead_category_label_snapshot categoryLabel
        FROM zsjos_lead l
        WHERE l.tenant_id=#{tenantId} AND l.deleted=0
          AND l.contribution_user_id_snapshot IS NOT NULL AND l.submitted_at IS NOT NULL
        <choose>
          <when test="scopeType == 'USER'">AND l.contribution_user_id_snapshot=#{scopeId}</when>
          <when test="scopeType == 'DEPT'">AND l.contribution_dept_id_snapshot=#{scopeId}</when>
          <when test="scopeType == 'CENTER'">
            <choose>
              <when test="centerDeptIds != null and centerDeptIds.size() > 0">
                AND l.contribution_dept_id_snapshot IN
                <foreach collection="centerDeptIds" item="deptId" open="(" separator="," close=")">#{deptId}</foreach>
              </when>
              <otherwise>AND 1=0</otherwise>
            </choose>
          </when>
        </choose>
        ORDER BY l.submitted_at DESC,l.id DESC
        </script>
        """)
    List<MediaLeadFact> leads(@Param("tenantId") Long tenantId, @Param("scopeType") String scopeType,
                              @Param("scopeId") Long scopeId, @Param("centerDeptIds") List<Long> centerDeptIds);

    @Select("""
        <script>
        SELECT o.lead_id leadId,MIN(o.effective_at) effectiveAt
        FROM zsjos_order o
        JOIN zsjos_lead l ON l.id=o.lead_id AND l.tenant_id=o.tenant_id AND l.deleted=0
        WHERE o.tenant_id=#{tenantId} AND o.deleted=0 AND o.status='effective'
          AND o.order_type &lt;&gt; 'repurchase' AND o.lead_id IS NOT NULL AND o.effective_at IS NOT NULL
        """ + CONVERSION_AMOUNT_CONDITION + """
          AND l.contribution_user_id_snapshot IS NOT NULL AND l.submitted_at IS NOT NULL
        <choose>
          <when test="scopeType == 'USER'">AND l.contribution_user_id_snapshot=#{scopeId}</when>
          <when test="scopeType == 'DEPT'">AND l.contribution_dept_id_snapshot=#{scopeId}</when>
          <when test="scopeType == 'CENTER'">
            <choose>
              <when test="centerDeptIds != null and centerDeptIds.size() > 0">
                AND l.contribution_dept_id_snapshot IN
                <foreach collection="centerDeptIds" item="deptId" open="(" separator="," close=")">#{deptId}</foreach>
              </when>
              <otherwise>AND 1=0</otherwise>
            </choose>
          </when>
        </choose>
        GROUP BY o.lead_id
        </script>
        """)
    List<MediaLeadOrderFact> firstOrders(@Param("tenantId") Long tenantId, @Param("scopeType") String scopeType,
                                         @Param("scopeId") Long scopeId, @Param("centerDeptIds") List<Long> centerDeptIds);
}
