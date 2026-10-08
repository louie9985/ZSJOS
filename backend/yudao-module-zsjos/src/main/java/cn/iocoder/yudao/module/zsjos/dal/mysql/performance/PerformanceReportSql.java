package cn.iocoder.yudao.module.zsjos.dal.mysql.performance;

/** Aggregate authorized facts before transporting them; keep the same frozen attribution definitions as details. */
public final class PerformanceReportSql {
    private PerformanceReportSql() {}
    private static String scoped(String source) { return PerformanceDetailSql.scoped(source); }
    private static String between(String column, String end) {
        return column + " &gt;= #{start} AND " + column + " &lt; #{" + end + "}";
    }
    private static final String MANUAL = "NOT (group_key IN ('lead_first_follow_up','lead_qualification') AND COALESCE(generation_source,'') IN ('sales_self_sourced_auto','education_self_sourced_auto'))";
    private static final String SOURCE = "CASE WHEN BINARY order_type='repurchase' THEN 'self' WHEN BINARY group_key IN ('inbound','self') THEN group_key ELSE 'unknown' END";

    public static String amounts(PerformanceReportQuery q) {
        boolean product = "product".equals(q.getGrouping());
        String dimension = switch (q.getGrouping()) {
            case "source" -> SOURCE;
            case "user" -> "CAST(user_id AS CHAR)";
            case "contributor" -> "BINARY CONCAT(COALESCE(CAST(user_id AS CHAR),'unknown'),'|',COALESCE(user_name,'历史姓名缺失'))";
            case "product" -> "BINARY CONCAT(COALESCE(group_key,'unknown'),'|',COALESCE(label,'历史产品名称缺失'))";
            default -> "'total'";
        };
        String labels = product ? "MAX(label)" : "NULL";
        String average = product ? "0" : "COALESCE(SUM(CASE WHEN amount NOT IN (0,0.01) THEN amount ELSE 0 END),0)";
        String averageCount = product ? "0" : "COUNT(CASE WHEN amount NOT IN (0,0.01) THEN 1 END)";
        return "<script>WITH facts AS (" + scoped(product ? PerformanceFactSql.PRODUCTS : PerformanceFactSql.ORDERS)
                + "), windows AS (<foreach collection='intervals' item='w' separator=' UNION ALL '>SELECT #{w.key} window_key,#{w.start} starts,#{w.end} ends</foreach>) "
                + "SELECT w.window_key `key`, " + dimension + " group_key," + labels + " label,"
                + ("user".equals(q.getGrouping()) ? "user_id" : "NULL") + " user_id,COALESCE(SUM(amount),0) amount,"
                + (product ? "COUNT(DISTINCT lead_id)" : "COUNT(f.id)") + " count," + average + " average_amount," + averageCount + " average_orders "
                + "FROM windows w LEFT JOIN facts f ON f.occurred_at &gt;= w.starts AND f.occurred_at &lt; w.ends "
                + "GROUP BY w.window_key" + ("total".equals(q.getGrouping()) ? "" : "," + dimension)
                + ("user".equals(q.getGrouping()) ? ",user_id" : "") + "</script>";
    }
    public static String receipts(PerformanceReportQuery q) {
        return "<script>WITH receipts AS (" + scoped(PerformanceFactSql.RECEIPTS) + ") SELECT * FROM receipts WHERE "
                + between("received_at", "end") + " ORDER BY occurred_at DESC,id DESC</script>";
    }
    public static String cohortOrders(PerformanceReportQuery q) {
        // A monthly receipt cohort observes subsequent conversions, not just orders submitted in that month.
        return "<script>WITH receipts AS (" + scoped(PerformanceFactSql.RECEIPTS) + "), orders AS (" + scoped(PerformanceFactSql.ORDERS)
                + ") SELECT o.* FROM orders o WHERE o.order_type='first_purchase' AND EXISTS (SELECT 1 FROM receipts r WHERE "
                + between("r.received_at", "end") + " AND r.lead_id=o.lead_id AND r.user_id=o.user_id AND r.received_at=o.received_at)</script>";
    }
    public static String calendarTasks(PerformanceReportQuery q) {
        return "<script>WITH receipts AS (" + scoped(PerformanceFactSql.RECEIPTS) + "), tasks AS (" + scoped(PerformanceFactSql.TASKS)
                + ") SELECT t.* FROM tasks t WHERE t.group_key='lead_qualification' AND EXISTS (SELECT 1 FROM receipts r WHERE "
                + between("r.received_at", "end") + " AND r.lead_id=t.lead_id AND r.user_id=t.user_id AND r.assignment_id=t.assignment_id) ORDER BY t.id DESC</script>";
    }
    public static String pending(PerformanceReportQuery q) {
        return "<script>WITH facts AS (" + scoped(PerformanceFactSql.TASKS) + "), manual AS (SELECT * FROM facts WHERE " + MANUAL + ") "
                + "SELECT 'accept' `key`,COUNT(*) count FROM manual WHERE status='pending' AND group_key='lead_assignment_accept' UNION ALL "
                + "SELECT 'qualification',COUNT(*) FROM manual WHERE current_qualification=TRUE AND (status='pending' OR outcome='overdue') AND group_key='lead_qualification' AND due_at &lt;= #{now} UNION ALL "
                + "SELECT 'todayFollowUp',COUNT(*) FROM manual WHERE status='pending' AND group_key IN ('lead_first_follow_up','lead_follow_up_reminder') AND due_at &gt;= #{today} AND due_at &lt; #{tomorrow} UNION ALL "
                + "SELECT 'overdueFollowUp',COUNT(*) FROM manual WHERE status='pending' AND group_key IN ('lead_first_follow_up','lead_follow_up_reminder') AND due_at &lt; #{now}</script>";
    }
    public static String activity(PerformanceReportQuery q) {
        return "<script>WITH assignments AS (" + scoped(PerformanceFactSql.ASSIGNMENTS) + "), followups AS (" + scoped(PerformanceFactSql.FOLLOW_UPS)
                + "), tasks AS (" + scoped(PerformanceFactSql.TASKS) + ") "
                + "SELECT CASE WHEN group_key='dispatch' THEN 'assigned' ELSE 'missed' END `key`,COUNT(DISTINCT lead_id) count FROM assignments WHERE " + between("occurred_at", "end") + " GROUP BY group_key UNION ALL "
                + "SELECT 'followUps',COUNT(*) FROM followups WHERE " + between("occurred_at", "end") + " UNION ALL "
                + "SELECT CASE WHEN status='completed' THEN 'completed' WHEN status='cancelled' THEN 'cancelled' WHEN due_at &lt; #{now} THEN 'overdue' ELSE 'pending' END,COUNT(*) FROM tasks WHERE "
                + MANUAL + " AND group_key IN ('lead_first_follow_up','lead_follow_up_reminder') AND status IN ('completed','cancelled','pending') AND " + between("due_at", "dueEnd") + " GROUP BY 1</script>";
    }
    public static String missingAttribution(PerformanceReportQuery q) {
        // The existing warning covers all readable historical orders, independent of the report period.
        return "<script>WITH orders AS (" + scoped(PerformanceFactSql.ORDERS) + ") SELECT COUNT(*) count,COALESCE(SUM(amount),0) amount FROM orders WHERE dept_id IS NULL OR center_id IS NULL</script>";
    }
}
