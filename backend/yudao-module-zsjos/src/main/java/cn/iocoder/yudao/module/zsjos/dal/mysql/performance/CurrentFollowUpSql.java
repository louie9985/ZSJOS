package cn.iocoder.yudao.module.zsjos.dal.mysql.performance;

/** Current responsibility deliberately ignores attribution rows, which may be missing or duplicated. */
public final class CurrentFollowUpSql {
    private CurrentFollowUpSql() {}

    public static boolean supports(String metric) {
        return "todayFollowUp".equals(metric) || "overdueFollowUp".equals(metric);
    }

    private static String tasks() {
        return "SELECT t.* FROM zsjos_business_task t WHERE t.tenant_id=#{tenant} AND t.deleted=0 "
                + "AND t.status='pending' AND t.task_type IN ('lead_first_follow_up','lead_follow_up_reminder') "
                + "AND NOT (t.task_type='lead_first_follow_up' AND COALESCE(JSON_UNQUOTE(JSON_EXTRACT(t.payload,'$.generationSource')),'') "
                + "IN ('sales_self_sourced_auto','education_self_sourced_auto')) "
                + "AND <choose><when test='assigneeIds != null and !assigneeIds.isEmpty()'>t.assignee_id IN "
                + "<foreach collection='assigneeIds' item='assignee' open='(' separator=',' close=')'>#{assignee}</foreach>"
                + "</when><otherwise>1=0</otherwise></choose>";
    }

    public static String pending() {
        return "<script>WITH tasks AS (" + tasks() + ") "
                + "SELECT 'todayFollowUp' `key`,COUNT(*) count FROM tasks WHERE due_at &gt;= #{today} AND due_at &lt; #{tomorrow} UNION ALL "
                + "SELECT 'overdueFollowUp',COUNT(*) FROM tasks WHERE due_at &lt; #{now}</script>";
    }

    static String relation(PerformanceDetailQuery q) {
        String due = "todayFollowUp".equals(q.getMetric())
                ? "t.due_at &gt;= #{today} AND t.due_at &lt; #{tomorrow}" : "t.due_at &lt; #{now}";
        return "tasks AS (" + tasks() + "), result AS (SELECT t.tenant_id,t.id,t.biz_id lead_id,t.assignee_id user_id,"
                + "t.create_time occurred_at,t.due_at,t.completed_at,t.status,t.task_type group_key,"
                + "l.lead_no number,l.owner_user_id,l.ownership_started_at received_at,"
                + "l.lead_category_label_snapshot category,l.sales_stage_label_snapshot stage "
                + "FROM tasks t LEFT JOIN zsjos_lead l ON l.id=t.biz_id AND l.tenant_id=t.tenant_id AND l.deleted=0 "
                + "WHERE " + due + ")";
    }
}
