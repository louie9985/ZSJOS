package cn.iocoder.yudao.module.zsjos.dal.mysql.lead;

import cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.calendar.LeadCalendarSearchReqVO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.calendar.CalendarSearchQuery;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Map;

public final class LeadCalendarSearchSql {
    private static String source(LeadCalendarSearchReqVO req) {
        // Aggregate every pending task before filtering dates: a later task must not move a lead's earliest deadline.
        return """
            FROM (
              SELECT l.id, l.lead_category AS category, MIN(t.due_at) AS deadline
              FROM zsjos_lead l JOIN zsjos_business_task t ON t.biz_id=l.id
                AND t.tenant_id=l.tenant_id AND t.deleted=0
              WHERE l.tenant_id=#{tenantId} AND l.deleted=0 AND l.owner_user_id=#{userId}
                AND t.assignee_type='user' AND t.assignee_id=#{userId}
                AND t.biz_type='lead' AND t.status='pending'
                AND t.task_type IN ('lead_first_follow_up','lead_follow_up_reminder')
                AND t.due_at IS NOT NULL
                AND (LOCATE(#{req.keyword},l.lead_no)>0 OR LOCATE(#{req.keyword},l.submitted_name)>0
                  OR LOCATE(#{req.keyword},l.submitted_mobile)>0 OR LOCATE(#{req.keyword},l.submitted_wechat_id)>0)
              GROUP BY l.id,l.lead_category
            ) due WHERE 1=1
            """ + (req.getRangeStart() == null ? "" : " AND DATE(deadline)>=#{req.rangeStart}")
                + (req.getRangeEnd() == null ? "" : " AND DATE(deadline)<=#{req.rangeEnd}");
    }
    public static String count(Map<String, Object> args) {
        return "SELECT COUNT(*) " + source((LeadCalendarSearchReqVO) args.get("req"));
    }
    public static String page(Map<String, Object> args) {
        var req = (LeadCalendarSearchReqVO) args.get("req");
        return "SELECT id,category,deadline " + source(req) + " "
                + CalendarSearchQuery.order(req, "deadline", "deadline", "id", LocalDate.now(ZoneId.of("Asia/Shanghai")))
                + " LIMIT #{req.pageSize} OFFSET #{offset}";
    }
}
