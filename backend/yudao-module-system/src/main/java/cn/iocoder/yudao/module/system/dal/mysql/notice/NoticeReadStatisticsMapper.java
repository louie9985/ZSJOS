package cn.iocoder.yudao.module.system.dal.mysql.notice;

import cn.iocoder.yudao.module.system.controller.admin.notice.vo.*;
import org.apache.ibatis.annotations.*;
import java.util.List;

/** Explicit tenant joins protect historical profiles and aggregate subqueries. */
@Mapper
public interface NoticeReadStatisticsMapper {
    String ROWS = """
        <choose>
          <when test="q.scope == 'EXTRA' or q.scope == 'ACTUAL'">
            SELECT rr.user_id, u.nickname AS user_name, u.dept_id, d.name AS dept_name,
              'CURRENT' AS profile_source, u.status AS account_status,
              CASE WHEN u.id IS NULL THEN 1 ELSE 0 END AS account_deleted, rr.read_time
            FROM system_notice_read rr
            LEFT JOIN system_users u ON u.id=rr.user_id AND u.tenant_id=rr.tenant_id AND u.deleted=0
            LEFT JOIN system_dept d ON d.id=u.dept_id AND d.tenant_id=rr.tenant_id AND d.deleted=0
            WHERE rr.notice_id=#{q.id} AND rr.tenant_id=#{tenantId} AND rr.deleted=0
            <if test="q.scope == 'EXTRA'">
              AND NOT EXISTS (SELECT 1 FROM system_notice_recipient r WHERE r.notice_id=rr.notice_id
                AND r.user_id=rr.user_id AND r.tenant_id=rr.tenant_id AND r.deleted=0)
            </if>
          </when>
          <otherwise>
            SELECT r.user_id,
              CASE WHEN r.profile_snapshot_complete=1 THEN r.user_name_snapshot ELSE u.nickname END AS user_name,
              CASE WHEN r.profile_snapshot_complete=1 THEN r.dept_id_snapshot ELSE u.dept_id END AS dept_id,
              CASE WHEN r.profile_snapshot_complete=1 THEN r.dept_name_snapshot ELSE d.name END AS dept_name,
              CASE WHEN r.profile_snapshot_complete=1 THEN 'SNAPSHOT' ELSE 'CURRENT' END AS profile_source,
              u.status AS account_status, CASE WHEN u.id IS NULL THEN 1 ELSE 0 END AS account_deleted, rr.read_time
            FROM system_notice_recipient r
            LEFT JOIN system_notice_read rr ON rr.notice_id=r.notice_id AND rr.user_id=r.user_id
              AND rr.tenant_id=r.tenant_id AND rr.deleted=0
            LEFT JOIN system_users u ON u.id=r.user_id AND u.tenant_id=r.tenant_id AND u.deleted=0
            LEFT JOIN system_dept d ON d.id=u.dept_id AND d.tenant_id=r.tenant_id AND d.deleted=0
            WHERE r.notice_id=#{q.id} AND r.tenant_id=#{tenantId} AND r.deleted=0
            <if test="q.scope == 'READ'">AND rr.id IS NOT NULL</if>
            <if test="q.scope == 'UNREAD'">AND rr.id IS NULL</if>
          </otherwise>
        </choose>
        """;
    String FILTER = """
        <where>
          <if test="q.name != null and q.name != ''">LOCATE(#{q.name}, t.user_name) > 0</if>
          <if test="q.deptId != null">AND t.dept_id=#{q.deptId}</if>
        </where>
        """;

    @Select("<script>SELECT COUNT(*) FROM (" + ROWS + ") t " + FILTER + "</script>")
    long count(@Param("tenantId") Long tenantId, @Param("q") NoticeReadPageReqVO q);

    @Select("<script>SELECT * FROM (" + ROWS + ") t " + FILTER
            + " ORDER BY t.user_id LIMIT #{q.pageSize} OFFSET #{offset}</script>")
    List<NoticeReadPersonRespVO> page(@Param("tenantId") Long tenantId, @Param("q") NoticeReadPageReqVO q,
                                    @Param("offset") long offset);

    @Select("<script>SELECT t.dept_id AS id, MIN(t.dept_name) AS name FROM (" + ROWS
            + ") t WHERE t.dept_id IS NOT NULL GROUP BY t.dept_id ORDER BY t.dept_id</script>")
    List<NoticeReadSummaryRespVO.Department> departments(@Param("tenantId") Long tenantId,
                                                       @Param("q") NoticeReadPageReqVO q);
}
