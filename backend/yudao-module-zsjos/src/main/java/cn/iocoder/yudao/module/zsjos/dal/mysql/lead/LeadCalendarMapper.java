package cn.iocoder.yudao.module.zsjos.dal.mysql.lead;

import lombok.Data;
import org.apache.ibatis.annotations.*;
import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface LeadCalendarMapper {
    @Data
    class DueLead {
        private Long id;
        private String category;
        private LocalDateTime deadline;
    }

    // Ownership and assignee must both match: transferred tasks never leak into the former owner's calendar.
    @Select("""
        SELECT l.id, l.lead_category AS category, MIN(t.due_at) AS deadline
        FROM zsjos_lead l JOIN zsjos_business_task t ON t.biz_id=l.id
          AND t.tenant_id=l.tenant_id AND t.deleted=0
        WHERE l.tenant_id=#{tenantId} AND l.deleted=0 AND l.owner_user_id=#{userId}
          AND t.assignee_type='user' AND t.assignee_id=#{userId}
          AND t.biz_type='lead' AND t.status='pending'
          AND t.task_type IN ('lead_first_follow_up','lead_follow_up_reminder')
          AND t.due_at IS NOT NULL
        GROUP BY l.id,l.lead_category
        HAVING MIN(t.due_at) >= #{start} AND MIN(t.due_at) < #{end}
        """)
    List<DueLead> selectDueLeads(@Param("tenantId") Long tenantId, @Param("userId") Long userId,
                               @Param("start") LocalDateTime start, @Param("end") LocalDateTime end);
}
