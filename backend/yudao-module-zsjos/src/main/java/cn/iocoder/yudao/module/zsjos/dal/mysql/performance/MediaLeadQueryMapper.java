package cn.iocoder.yudao.module.zsjos.dal.mysql.performance;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface MediaLeadQueryMapper {
    String SCOPE = """
        FROM zsjos_lead l WHERE l.tenant_id=#{tenant} AND l.deleted=0
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
    String PERIOD = " AND l.submitted_at &gt;= #{start} AND l.submitted_at &lt; #{end} ";
    String FIRST_ORDER = """
        (SELECT MIN(o.effective_at) FROM zsjos_order o
         WHERE o.tenant_id=l.tenant_id AND o.deleted=0 AND o.lead_id=l.id
           AND o.status='effective' AND o.order_type &lt;&gt; 'repurchase'
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
}
