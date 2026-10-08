package cn.iocoder.yudao.module.zsjos.dal.mysql.performance;

/** Count and page use the same authorized population, including historical duplicate facts. */
public final class PerformanceDetailSql {
    private PerformanceDetailSql() {}
    static String scoped(String fact) {
        String body = fact.replaceFirst("(?s)\\s*ORDER BY[^<]+</script>", "</script>")
                .replace("<script>", "").replace("</script>", "");
        return "SELECT f.* FROM (" + body + ") f WHERE <choose>"
                + "<when test=\"allDepartments\"><choose><when test=\"missingDepartment\">1=1</when>"
                + "<otherwise>f.dept_id IS NOT NULL</otherwise></choose></when>"
                + "<when test=\"departments != null and !departments.isEmpty()\">f.dept_id IN "
                + "<foreach collection=\"departments\" item=\"dept\" open=\"(\" separator=\",\" close=\")\">#{dept}</foreach></when>"
                + "<otherwise>1=0</otherwise></choose>";
    }
    private static String interval(String column, String end) {
        return column + " &gt;= #{start} AND " + column + " &lt; #{" + end + "}";
    }
    private static String equal(String expression) {
        return "CAST(" + expression + " AS BINARY)=CAST(#{groupKey} AS BINARY)";
    }
    private static String orderDefinitions(PerformanceDetailQuery q) {
        String result = "orders_base AS (" + scoped(PerformanceFactSql.ORDERS) + ")";
        if (q.getGroupKey() == null || q.getDimension() == null) return result + ", orders AS (SELECT * FROM orders_base)";
        return result + switch (q.getDimension()) {
            // Retain the legacy unknown key while matching the current report's display label.
            case "source" -> ", orders AS (SELECT * FROM orders_base WHERE CAST(CASE WHEN CAST(order_type AS BINARY)=CAST('repurchase' AS BINARY) OR CAST(group_key AS BINARY)=CAST('self' AS BINARY) THEN 'self|非引流' WHEN CAST(group_key AS BINARY)=CAST('inbound' AS BINARY) THEN 'inbound|线上引流' ELSE 'unknown|其他' END AS BINARY)"
                    + "=CAST(CASE WHEN #{groupKey}='unknown|历史来源缺失' THEN 'unknown|其他' ELSE #{groupKey} END AS BINARY))";
            case "contributor" -> ", orders AS (SELECT * FROM orders_base WHERE " + equal("COALESCE(CAST(user_id AS CHAR),'')") + ")";
            case "product" -> ", products AS (" + scoped(PerformanceFactSql.PRODUCTS) + "), product_amounts AS ("
                    + "SELECT lead_id,SUM(amount) amount FROM products WHERE " + equal("CONCAT(COALESCE(CAST(group_key AS CHAR),'unknown'),'|',COALESCE(label,'历史产品名称缺失'))")
                    + " GROUP BY lead_id), orders AS (SELECT o.id,o.number,o.lead_id,o.source_type,o.source_provider_user_id,o.user_id,o.user_name,"
                    + "o.occurred_at,p.amount,o.order_type,o.received_at,o.assignment_id,o.group_key,o.label,o.channel_code,o.dept_id,o.center_id "
                    + "FROM orders_base o JOIN product_amounts p ON p.lead_id=o.id)";
            default -> ", orders AS (SELECT * FROM orders_base)";
        };
    }
    public static String conversionOrders(PerformanceDetailQuery q) {
        return "<script>WITH " + orderDefinitions(q) + " SELECT * FROM orders WHERE " + interval("occurred_at", "end")
                + " ORDER BY occurred_at DESC,id DESC</script>";
    }
    public static String conversionReceipts(PerformanceDetailQuery q) {
        // Retain out-of-period histories for candidate leads without re-running the order query for normal pages.
        if(q.getConversionLeadIds().size() <= 512) {
            return "<script>WITH receipts AS (" + scoped(PerformanceFactSql.RECEIPTS) + ") SELECT r.* FROM receipts r WHERE ("
                    + interval("r.received_at", "end") + ") <if test=\"!conversionLeadIds.isEmpty()\"> OR r.lead_id IN "
                    + "<foreach collection=\"conversionLeadIds\" item=\"lead\" open=\"(\" separator=\",\" close=\")\">#{lead}</foreach></if> "
                    + "ORDER BY r.occurred_at DESC,r.id DESC</script>";
        }
        // Large periods use a relational predicate instead of an unbounded IN list.
        return "<script>WITH " + orderDefinitions(q) + ", receipts AS (" + scoped(PerformanceFactSql.RECEIPTS) + ") "
                + "SELECT r.* FROM receipts r WHERE (" + interval("r.received_at", "end") + ") OR EXISTS "
                + "(SELECT 1 FROM orders o WHERE o.lead_id=r.lead_id AND " + interval("o.occurred_at", "end") + ") "
                + "ORDER BY r.occurred_at DESC,r.id DESC</script>";
    }
    private static String relation(PerformanceDetailQuery q) {
        return switch (q.getMetric()) {
            case "orders" -> orderDefinitions(q) + ", result AS (SELECT * FROM orders WHERE " + interval("occurred_at", "end") + ")";
            case "leads", "valid" -> "receipts AS (" + scoped(PerformanceFactSql.RECEIPTS) + "), ranked AS ("
                    + "SELECT r.*,ROW_NUMBER() OVER(PARTITION BY lead_id ORDER BY occurred_at DESC,id DESC) receipt_rank FROM receipts r WHERE "
                    + interval("received_at", "end") + ("valid".equals(q.getMetric()) ? " AND status IN ('valid','converted','won')" : "")
                    + ("category".equals(q.getDimension()) ? " AND " + equal("COALESCE(category,'历史分类缺失')") : "")
                    + ("stage".equals(q.getDimension()) ? " AND " + equal("COALESCE(stage,'阶段未记录')") : "")
                    + "), result AS (SELECT lead_id id,number,COALESCE(category,'历史分类缺失') label,received_at occurred_at,status,"
                    + "occurred_at receipt_occurred_at,id receipt_id FROM ranked WHERE receipt_rank=1)";
            case "assigned", "missed", "followUps" -> "facts AS (" + scoped("followUps".equals(q.getMetric()) ? PerformanceFactSql.FOLLOW_UPS : PerformanceFactSql.ASSIGNMENTS)
                    + "), result AS (SELECT * FROM facts WHERE " + interval("occurred_at", "end")
                    + ("followUps".equals(q.getMetric()) ? "" : " AND group_key='" + ("assigned".equals(q.getMetric()) ? "dispatch" : "timeout") + "'") + ")";
            default -> "facts AS (" + scoped(PerformanceFactSql.TASKS) + "), result AS (SELECT * FROM facts WHERE "
                    + "NOT (group_key IN ('lead_first_follow_up','lead_qualification') AND COALESCE(generation_source,'') IN ('sales_self_sourced_auto','education_self_sourced_auto')) AND "
                    + switch (q.getMetric()) {
                        case "accept" -> "status='pending' AND group_key='lead_assignment_accept'";
                        case "qualification" -> "current_qualification=TRUE AND (status='pending' OR outcome='overdue') AND group_key='lead_qualification' AND due_at &lt;= #{now}";
                        case "todayFollowUp" -> "status='pending' AND group_key IN ('lead_first_follow_up','lead_follow_up_reminder') AND due_at &gt;= #{today} AND due_at &lt; #{tomorrow}";
                        case "overdueFollowUp" -> "status='pending' AND group_key IN ('lead_first_follow_up','lead_follow_up_reminder') AND due_at &lt; #{now}";
                        default -> "group_key IN ('lead_first_follow_up','lead_follow_up_reminder') AND " + interval("due_at", "dueEnd");
                    } + ")";
        };
    }
    public static String count(PerformanceDetailQuery q) {
        if ("leads".equals(q.getMetric()) || "valid".equals(q.getMetric())) return "<script>WITH receipts AS ("
                + scoped(PerformanceFactSql.RECEIPTS) + ") SELECT COUNT(DISTINCT lead_id) FROM receipts WHERE "
                + interval("received_at", "end") + ("valid".equals(q.getMetric()) ? " AND status IN ('valid','converted','won')" : "")
                + ("category".equals(q.getDimension()) ? " AND " + equal("COALESCE(category,'历史分类缺失')") : "")
                + ("stage".equals(q.getDimension()) ? " AND " + equal("COALESCE(stage,'阶段未记录')") : "") + "</script>";
        return "<script>WITH " + relation(q) + " SELECT COUNT(*) FROM result</script>";
    }
    public static String page(PerformanceDetailQuery q) {
        String order = switch (q.getMetric()) {
            case "leads", "valid" -> "receipt_occurred_at DESC,receipt_id DESC,id";
            case "orders", "assigned", "missed", "followUps" -> "occurred_at DESC,id DESC";
            default -> "CASE WHEN due_at IS NULL THEN 1 ELSE 0 END,due_at,id,assignment_id,outcome,completed_at";
        };
        return "<script>WITH " + relation(q) + " SELECT * FROM result ORDER BY " + order + " LIMIT #{size} OFFSET #{offset}</script>";
    }
    public static String firstDate(PerformanceDetailQuery q) {
        return "<script>WITH orders AS (" + scoped(PerformanceFactSql.ORDERS) + "), receipts AS (" + scoped(PerformanceFactSql.RECEIPTS)
                + ") SELECT MIN(at) FROM (SELECT MIN(occurred_at) at FROM orders UNION ALL SELECT MIN(received_at) at FROM receipts) dates</script>";
    }
}
