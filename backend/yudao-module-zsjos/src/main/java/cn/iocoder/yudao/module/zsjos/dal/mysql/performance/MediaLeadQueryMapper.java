package cn.iocoder.yudao.module.zsjos.dal.mysql.performance;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface MediaLeadQueryMapper {
    String SCOPE_WHERE = """
        l.tenant_id=#{tenant} AND l.deleted=0
          AND l.contribution_user_id_snapshot IS NOT NULL AND l.submitted_at IS NOT NULL
          AND l.submitted_at &lt;= #{now}
        <choose>
          <when test="type == 'USER'">AND l.contribution_user_id_snapshot=#{id}</when>
          <when test="type == 'DEPT'">AND l.contribution_dept_id_snapshot=#{id}</when>
          <when test="type == 'CENTER' and departments != null and !departments.isEmpty()">
            AND l.contribution_dept_id_snapshot IN
            <foreach collection="departments" item="dept" open="(" separator="," close=")">#{dept}</foreach>
          </when>
          <otherwise>AND 1=0</otherwise>
        </choose>
        """;
    String SCOPE = " FROM zsjos_lead l WHERE " + SCOPE_WHERE;
    String PERIOD = " AND l.submitted_at &gt;= #{start} AND l.submitted_at &lt; #{end} ";
    String FIRST_ORDER = """
        (SELECT MIN(o.effective_at) FROM zsjos_order o
         WHERE o.tenant_id=l.tenant_id AND o.deleted=0 AND o.lead_id=l.id
           AND o.status='effective' AND o.order_type &lt;&gt; 'repurchase'
        """ + MediaLeadFactMapper.CONVERSION_AMOUNT_CONDITION + """
           AND o.effective_at &lt;= #{now})
        """;

    @Select("<script>SELECT COUNT(*) " + SCOPE + PERIOD + "</script>")
    long countDetails(@Param("tenant") Long tenant, @Param("type") String type, @Param("id") Long id,
                      @Param("departments") List<Long> departments, @Param("start") LocalDateTime start,
                      @Param("end") LocalDateTime end, @Param("now") LocalDateTime now);

    @Select("<script>SELECT l.lead_no leadNo,l.submitted_at submittedAt,"
            + "l.contribution_user_name_snapshot contributorName,l.status,"
            + "l.source_channel_label_snapshot channelLabel,l.lead_category_label_snapshot categoryLabel,"
            + FIRST_ORDER + " orderEffectiveAt " + SCOPE + PERIOD
            + " ORDER BY l.submitted_at DESC,l.id DESC LIMIT #{size} OFFSET #{offset}</script>")
    List<MediaLeadDetailRow> pageDetails(@Param("tenant") Long tenant, @Param("type") String type, @Param("id") Long id,
                                       @Param("departments") List<Long> departments, @Param("start") LocalDateTime start,
                                       @Param("end") LocalDateTime end, @Param("now") LocalDateTime now,
                                       @Param("offset") long offset, @Param("size") int size);

    // Select one first purchase deterministically. Conversion and contribution have different amount rules.
    String DRILL_ORDER = """
        (SELECT o.id FROM zsjos_order o WHERE o.tenant_id=l.tenant_id AND o.deleted=0
          AND o.lead_id=l.id AND o.status='effective' AND o.order_type &lt;&gt; 'repurchase'
          AND o.effective_at IS NOT NULL AND o.effective_at &lt;= #{now}
          <if test="q.metric != 'contribution'"> """ + MediaLeadFactMapper.CONVERSION_AMOUNT_CONDITION + """
          </if>
          ORDER BY o.effective_at,o.id LIMIT 1)
        """;
    String DRILL_FILTER = """
        <if test="q.memberUserId != null">AND l.contribution_user_id_snapshot=#{q.memberUserId}</if>
        <choose>
          <when test="q.metric == 'memberConverted'">
            AND """ + FIRST_ORDER + """
              &gt;= #{start} AND """ + FIRST_ORDER + """
              &lt; #{end}
          </when>
          <otherwise>
            <if test="!q.cumulative"> AND l.submitted_at &gt;= #{start} AND l.submitted_at &lt; #{end} </if>
          </otherwise>
        </choose>
        <if test="q.metric == 'valid' or q.metric == 'converted' or q.metric == 'contribution'">
          AND l.status IN ('valid','converted','won')
        </if>
        <if test="q.metric == 'invalid'">AND l.status='invalid'</if>
        <if test="q.metric == 'converted'">AND """ + FIRST_ORDER + """
          IS NOT NULL</if>
        <if test="q.metric == 'contribution'">AND """ + DRILL_ORDER + """
          IS NOT NULL</if>
        <if test="q.dimension == 'channel'">AND COALESCE(l.source_channel_label_snapshot,'历史未记录')=#{q.groupLabel}</if>
        <if test="q.dimension == 'category'">AND COALESCE(l.lead_category_label_snapshot,'历史未记录')=#{q.groupLabel}</if>
        """;

    @Select("<script>SELECT COUNT(*) " + SCOPE + DRILL_FILTER + "</script>")
    long countDrill(@Param("tenant") Long tenant, @Param("type") String type, @Param("id") Long id,
                    @Param("departments") List<Long> departments, @Param("start") LocalDateTime start,
                    @Param("end") LocalDateTime end, @Param("now") LocalDateTime now,
                    @Param("q") cn.iocoder.yudao.module.zsjos.controller.admin.performance.vo.MediaLeadVO.DrillQuery query);

    @Select("<script>SELECT l.lead_no leadNo,l.submitted_at submittedAt,"
            + "l.contribution_user_name_snapshot contributorName,l.status,"
            + "l.source_channel_label_snapshot channelLabel,l.lead_category_label_snapshot categoryLabel,"
            + "l.submitted_name submittedName,l.provider_owner_name_snapshot providerName,"
            + "o.id orderId,o.order_no orderNo,o.total_amount orderAmount,o.effective_at orderEffectiveAt "
            + " FROM zsjos_lead l LEFT JOIN zsjos_order o ON o.id=" + DRILL_ORDER
            + " AND o.tenant_id=l.tenant_id AND o.deleted=0"
            + " WHERE " + SCOPE_WHERE + DRILL_FILTER
            + " ORDER BY l.submitted_at DESC,l.id DESC LIMIT #{size} OFFSET #{offset}</script>")
    List<MediaLeadDetailRow> pageDrill(@Param("tenant") Long tenant, @Param("type") String type, @Param("id") Long id,
                    @Param("departments") List<Long> departments, @Param("start") LocalDateTime start,
                    @Param("end") LocalDateTime end, @Param("now") LocalDateTime now,
                    @Param("q") cn.iocoder.yudao.module.zsjos.controller.admin.performance.vo.MediaLeadVO.DrillQuery query,
                    @Param("offset") long offset, @Param("size") int size);
}
